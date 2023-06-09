package com.sap.ase.poker.service;

import com.sap.ase.poker.model.GameState;
import com.sap.ase.poker.model.Action;
import com.sap.ase.poker.model.IllegalActionException;
import com.sap.ase.poker.model.IllegalAmountException;
import com.sap.ase.poker.model.Player;
import com.sap.ase.poker.model.deck.Card;
import com.sap.ase.poker.model.deck.Deck;
import com.sap.ase.poker.model.rules.WinnerRules;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class TableService {

    private final Supplier<Deck> deckSupplier;
    private final WinnerRules winnerRules;
    List<Player> players;
    GameState state;
    Player currentPlayer;
    List<Card> communityCards;
    Player winner;
    int pot;
    int roundPlayers;

    public TableService(Supplier<Deck> deckSupplier, WinnerRules winnerRules) {
        this.deckSupplier = deckSupplier;
        this.winnerRules = winnerRules;
        this.players = new ArrayList<>();
        this.state = GameState.OPEN;
        this.communityCards = new ArrayList<>();
    }

    public GameState getState() {
        return this.state;
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public List<Card> getPlayerCards(String playerId) {
        List<Card> cards = new ArrayList<>();
        for(Player player: this.players){
            if(player.getId().equals(playerId)){
                cards.addAll(player.getHandCards());
            }
        }
        return cards;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public Optional<Player> getCurrentPlayer() {
        if(state == GameState.OPEN){
            return Optional.empty();
        }
        return Optional.of(currentPlayer);
    }

    public Map<String, Integer> getBets() {
        Map<String, Integer> bets = new HashMap<>();

        for(Player player: players){
            bets.put(player.getId(), player.getBet());
        }
        return bets;
    }

    public int getPot() {
        return this.pot;
    }

    public Optional<Player> getWinner() {
        if (state != GameState.ENDED){
            return Optional.empty();
        }   else{
            return Optional.ofNullable(getWinners().get(0));
        }
    }

    public List<Player> getWinners(){
        List<Player> activePlayers = getActivePlayers();
        if(activePlayers.size() == 1){
            return activePlayers;
        } else {
            return winnerRules.findWinners(communityCards, getActivePlayers()).getWinners();
        }
    }

    public List<Card> getWinnerHand() {
        Optional<Player> winner = getWinner();
        if (getActivePlayers().size() == 1 || !winner.isPresent()){
            return new ArrayList<>();
        }
        return winner.get().getHandCards();
    }

    public void resetGame() {
        this.state = GameState.OPEN;
        this.communityCards = new ArrayList<>();
        this.pot = 0;
        this.winner = null;
    }

    public void start() {
        // reset game
        resetGame();
        if (players.size() < 2) {
            return;
        }
        this.pot = 0;
        this.roundPlayers = 0;
        state = GameState.PRE_FLOP;
        for(Player player: players){
            List<Card> handCards = new ArrayList<>();
            handCards.add(deckSupplier.get().draw());
            handCards.add(deckSupplier.get().draw());

            player.setHandCards(handCards);
            player.setActive();
            this.roundPlayers = this.roundPlayers + 1;
        }
        this.currentPlayer = players.get(0);
    }

    public void addPlayer(String playerId, String playerName) {
        boolean exists = players.stream().anyMatch(obj -> obj.getId().equals(playerId));
        if(!exists) {
            Player player = new Player(playerId, playerName, 100);
            players.add(player);
        }
    }

    public void performAction(String action, int amount) throws IllegalAmountException {
        if(state==GameState.ENDED){
            return;
        }
        if (action.equals(Action.CHECK.getValue())) {
            handleCheck();
        } else if (action.equals(Action.CALL.getValue())) {
            handleCall();
        } else if (action.equals(Action.FOLD.getValue())) {
            handleFold();
        } else if (action.equals(Action.RAISE.getValue())) {
            handleRaise(amount);
        }

        this.roundPlayers = this.roundPlayers - 1;
        System.out.printf("Player %s performed action: %s, amount: %d%n", currentPlayer.getName(), action, amount);

        if (this.checkRoundComplete() || this.state==GameState.ENDED){
            this.roundEndActivities();
        }

        if(this.state==GameState.ENDED){
            distributeWinnings();
        }else {
            this.currentPlayer = this.getNextPlayer();
        }
    }


    public void handleCheck() {
        int currentMaxBet = this.getCurrentMaxBet();
        if(currentMaxBet!=0){
            throw new IllegalActionException("The player can not check as someone already placed a bet in this round.");
        }
    }

    public void handleRaise(int amount) {
        int currentMaxBet = this.getCurrentMaxBet();
        int minimumCash = players.stream()
                .filter(player ->  player.isActive()).mapToInt(v -> v.getCash()).min().getAsInt();

        if((amount + currentPlayer.getBet()) <= currentMaxBet){
            throw new IllegalAmountException("Bet Amount is not higher than Current Bet.");
        } else if (amount > currentPlayer.getCash()) {
            throw new IllegalAmountException("The player does not have enough cash to Bet this amount.");
        } else if (minimumCash < amount) {
            throw new IllegalAmountException("The betting amount exceeds other players remaining cash.");
        }
        currentPlayer.bet(amount + currentPlayer.getBet());
    }
    public void handleCall(){
        int currentMaxBet = this.getCurrentMaxBet();
        if (currentMaxBet > currentPlayer.getCash()) {
            throw new IllegalAmountException("The player does not have enough cash to Bet this amount.");
        } else if (currentMaxBet == 0) {
            throw new IllegalActionException("Call can't be performed since no player has raised the bet.");
        }

        currentPlayer.bet(currentMaxBet-currentPlayer.getBet());
    }

    public void handleFold(){
        currentPlayer.setInactive();
        if(getActivePlayers().size()==1){
            this.state = GameState.ENDED;
            this.winner = this.getNextPlayer();
        }
    }
    
    public int getCurrentMaxBet() {
        int maxBet = 0;
        List<Player> activePlayers = getActivePlayers();

        for(Player player:activePlayers){
            if(maxBet<player.getBet()){
                maxBet = player.getBet();
            }
        }

        return maxBet;
    }

    public Player getNextPlayer(){
        List<Player> activePlayers = getActivePlayers();
        int indexOfCurrentPlayer = activePlayers.indexOf(currentPlayer);

        Player nextActivePlayer = activePlayers.get((indexOfCurrentPlayer+1) % activePlayers.size());
        return nextActivePlayer;
    }

    public boolean checkRoundComplete(){
        List<Player> activePlayers = getActivePlayers();
        int bet = activePlayers.get(0).getBet();

        if(this.roundPlayers == 0){
            for(Player player: activePlayers){
                if(player.getBet() != bet){
                    this.roundPlayers = activePlayers.size();
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void roundEndActivities(){
        // collect pot
        int totalBet = 0;
        for(int bet: getBets().values()){
            totalBet = totalBet + bet;
        }
        pot = pot + totalBet;

        // clear bets
        for(Player player: this.players){
            player.clearBet();
        }

        // refresh round players
        this.roundPlayers = getActivePlayers().size();

        if(state == GameState.PRE_FLOP){
            communityCards.add(deckSupplier.get().draw());
            communityCards.add(deckSupplier.get().draw());
            communityCards.add(deckSupplier.get().draw());

            state = GameState.FLOP;
        } else if (state == GameState.FLOP) {
            communityCards.add(deckSupplier.get().draw());
            state = GameState.TURN;
        } else if (state == GameState.TURN) {
            communityCards.add(deckSupplier.get().draw());
            state = GameState.RIVER;
        } else if (state == GameState.RIVER) {
            state = GameState.ENDED;
            distributeWinnings();
        }
    }

    public List<Player> getActivePlayers(){
        List<Player> activePlayers = players.stream()
                .filter(player ->  player.isActive())
                .collect(Collectors.toList());
        return activePlayers;
    }

    public void distributeWinnings(){
        List<Player> winners = getWinners();
        for(Player winner: winners){
            winner.addCash(pot/winners.size());
        }
        for(Player player: players){
            player.clearBet();
        }
        pot = 0;
    }
}
