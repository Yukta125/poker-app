package com.sap.ase.poker.service;

import com.sap.ase.poker.model.*;
import com.sap.ase.poker.model.deck.*;
import com.sap.ase.poker.model.rules.HandRules;
import com.sap.ase.poker.model.rules.WinnerRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class TableServiceTest {
    private static final String PLAYER_1_ID="1";
    private static final String PLAYER_2_ID="2";

    private static final String PLAYER_3_ID="3";
    private static final String PLAYER_1_NAME="Jack";
    private static final String PLAYER_2_NAME="Rose";

    private static final String PLAYER_3_NAME="Mary";
    private static final int INITIAL_CASH=100;
    TableService tableService;
    Deck deck;


    @BeforeEach
    public void setUp() {
        CardShuffler shuffler = Mockito.mock(CardShuffler.class);
        deck = new Deck(new PokerCardsSupplier().get(), shuffler);
        WinnerRules winnerRules = new WinnerRules(new HandRules());
        tableService = new TableService(() -> deck, winnerRules);
    }

    public void addPlayers () {
        tableService.addPlayer(PLAYER_1_ID, PLAYER_1_NAME);
        tableService.addPlayer(PLAYER_2_ID, PLAYER_2_NAME);
    }

    public void setActivePlayers() {
        for(Player player: tableService.players) {
            player.setActive();
        }
    }

    public void setHands() {
        for(Player player: tableService.getPlayers()){
            List<Card> handCards = new ArrayList<>();
            handCards.add(deck.draw());
            handCards.add(deck.draw());

            player.setHandCards(handCards);
            player.setActive();
        }
    }

    public void addCommunityCards(int n) {
        for(int i=0; i<n; i++){
            tableService.communityCards.add(deck.draw());
        }
    }

    @Test
    void checkIfGameIsOpen() {
        assertThat(tableService.getState()).isEqualTo(GameState.OPEN);
    }

    @Test
    void getPlayersShouldReturnListOfCurrentPlayers() {
        // without adding players
        assertThat(tableService.getPlayers()).isEqualTo(Collections.EMPTY_LIST);

        // after adding players
        tableService.addPlayer(PLAYER_1_ID, PLAYER_1_NAME);
        tableService.addPlayer(PLAYER_2_ID, PLAYER_2_NAME);

        // it should return added players - with default 100 cash and with inactive status
        assertThat(tableService.getPlayers().size()).isEqualTo(2);
        assertThat(tableService.getPlayers().get(0).getId()).isEqualTo(PLAYER_1_ID);
        assertThat(tableService.getPlayers().get(1).getId()).isEqualTo(PLAYER_2_ID);
        assertThat(tableService.getPlayers().get(0).getName()).isEqualTo(PLAYER_1_NAME);
        assertThat(tableService.getPlayers().get(1).getName()).isEqualTo(PLAYER_2_NAME);
        assertThat(tableService.getPlayers().get(0).isActive()).isFalse();
        assertThat(tableService.getPlayers().get(1).isActive()).isFalse();
        assertThat(tableService.getPlayers().get(0).getCash()).isEqualTo(INITIAL_CASH);
        assertThat(tableService.getPlayers().get(1).getCash()).isEqualTo(INITIAL_CASH);
    }

    @Test
    void startGameProperly() {
        addPlayers();
        // there should not be any current player
        assertThat(tableService.getCurrentPlayer()).isEqualTo(Optional.empty());
        // it should return emptyList for getPlayerCards
        assertThat(tableService.getPlayerCards(PLAYER_1_ID).size()).isEqualTo(0);

        tableService.start();

        // game state should be PRE_FLOP
        assertThat(tableService.getState()).isEqualTo(GameState.PRE_FLOP);
        // both players should be active
        assertThat(tableService.getPlayers().get(0).isActive()).isTrue();
        assertThat(tableService.getPlayers().get(1).isActive()).isTrue();
        // both players should have two cards
        assertThat(tableService.getPlayerCards(PLAYER_1_ID).size()).isEqualTo(2);
        assertThat(tableService.getPlayerCards(PLAYER_2_ID).size()).isEqualTo(2);
        // first player should be the current player
        assertThat(tableService.getCurrentPlayer().get().getId()).isEqualTo(PLAYER_1_ID);
    }


    @Test
    void startGameWithOnePerson() {
        tableService.addPlayer(PLAYER_1_ID, PLAYER_1_NAME);

        tableService.start();
        assertThat(tableService.getState()).isEqualTo(GameState.OPEN);
    }


    @Test
    void getCurrentPlayerAfterPreFlop(){
        addPlayers();
        tableService.start();

        assertThat(tableService.getCurrentPlayer().get().getId().equals(PLAYER_1_ID));
    }

    @Test
    void getEmptyHandBeforeStart(){
        addPlayers();
        assertThat(tableService.getPlayerCards(PLAYER_1_ID).size() == 0);
    }

    @Test
    void getFullHandAfterStart(){
        addPlayers();

        tableService.start();
        assertThat(tableService.getPlayerCards(PLAYER_1_ID).size() == 2);
    }

    @Test
    void getEmptyCommunityCards(){
        assertThat(tableService.getCommunityCards().size() == 0);
    }

    @Test
    void testCall(){
        addPlayers();
        setActivePlayers();

        tableService.players.get(0).bet(10);
        tableService.currentPlayer = tableService.players.get(1);

        tableService.handleCall();

        assertThat(tableService.currentPlayer.getBet()).isEqualTo(10);
    }

    @Test
    void testCallNoRaiseBefore(){
        addPlayers();
        setActivePlayers();

        tableService.currentPlayer = tableService.players.get(0);
        assertThrows(IllegalActionException.class, ()->tableService.handleCall());
    }

    @Test
    void testCallLessCash(){
        addPlayers();
        setActivePlayers();

        tableService.players.get(0).bet(200);
        tableService.currentPlayer = tableService.players.get(1);

        assertThrows(IllegalAmountException.class, ()->tableService.handleCall());
    }

    @Test
    void handleFoldEndsGame(){
        addPlayers();
        setActivePlayers();

        tableService.currentPlayer = tableService.players.get(0);
        tableService.handleFold();

        assertThat(tableService.state).isEqualTo(GameState.ENDED);
        assertThat(tableService.winner).isEqualTo(tableService.players.get(1));
    }

    @Test
    void handleFold(){
        addPlayers();
        tableService.addPlayer(PLAYER_3_ID, PLAYER_3_NAME);
        setActivePlayers();

        tableService.currentPlayer = tableService.players.get(0);
        tableService.handleFold();

        assertThat(tableService.getActivePlayers().size()).isEqualTo(2);
    }


    @Test
    void handleCheck() {
        addPlayers();
        setActivePlayers();

        tableService.handleCheck();
        assertThat(tableService.getCurrentMaxBet()).isEqualTo(0);
    }

    @Test
    void handleCheckWithMaxBetNonZero() {
        addPlayers();
        setActivePlayers();

        tableService.players.get(0).bet(10);
        tableService.currentPlayer = tableService.players.get(1);

        assertThrows(IllegalActionException.class, ()->tableService.handleCheck());
    }

    @Test
    void handleRaise() {
        addPlayers();
        setActivePlayers();

        tableService.currentPlayer = tableService.players.get(0);
        tableService.handleRaise(10);

        assertThat(tableService.getCurrentMaxBet()).isEqualTo(10);
    }

    @Test
    void handleRaiseLessCash() {
        addPlayers();
        setActivePlayers();

        tableService.currentPlayer = tableService.players.get(0);

        assertThrows(IllegalAmountException.class, ()->tableService.handleRaise(200));
    }

    @Test
    void handleRaiseLessAmount() {
        addPlayers();
        setActivePlayers();

        tableService.players.get(0).bet(10);
        tableService.currentPlayer = tableService.players.get(1);

        assertThrows(IllegalAmountException.class, ()->tableService.handleRaise(5));
    }

    @Test
    void handleRaiseMinimumCashOthers() {
        addPlayers();
        setActivePlayers();

        tableService.players.get(0).deductCash(50);
        tableService.currentPlayer = tableService.players.get(1);

        assertThrows(IllegalAmountException.class, ()->tableService.handleRaise(60));
    }

    @Test
    void checkRoundCompleteWithUnequalBets() {
        addPlayers();
        setActivePlayers();

        tableService.roundPlayers = 0;
        tableService.getPlayers().get(0).bet(10);

        assertThat(tableService.checkRoundComplete()).isFalse();
    }

    @Test
    void checkRoundCompleteWithEqualBets() {
        addPlayers();
        setActivePlayers();

        tableService.roundPlayers = 0;
        tableService.getPlayers().get(0).bet(10);
        tableService.getPlayers().get(1).bet(10);

        assertThat(tableService.checkRoundComplete()).isTrue();
    }

    @Test
    void checkRoundCompleteWithPlayersLeft() {
        addPlayers();
        setActivePlayers();

        tableService.roundPlayers = 1;

        assertThat(tableService.checkRoundComplete()).isFalse();
    }

    @Test
    void testPreFlopRoundEndActivities() {
        tableService.state = GameState.PRE_FLOP;

        tableService.roundEndActivities();

        assertThat(tableService.getCommunityCards().size()).isEqualTo(3);
        assertThat(tableService.getState()).isEqualTo(GameState.FLOP);
    }

    @Test
    void testFlopRoundEndActivities() {
        tableService.state = GameState.FLOP;
        addCommunityCards(3);
        tableService.roundEndActivities();

        assertThat(tableService.getCommunityCards().size()).isEqualTo(4);
        assertThat(tableService.getState()).isEqualTo(GameState.TURN);
    }

    @Test
    void testTurnRoundEndActivities() {
        tableService.state = GameState.TURN;
        addCommunityCards(4);

        tableService.roundEndActivities();

        assertThat(tableService.getCommunityCards().size()).isEqualTo(5);
        assertThat(tableService.getState()).isEqualTo(GameState.RIVER);
    }

    @Test
    void testRiverRoundEndActivities() {
        addPlayers();
        setActivePlayers();
        setHands();
        addCommunityCards(5);
        tableService.state = GameState.RIVER;

        tableService.roundEndActivities();

        assertThat(tableService.getCommunityCards().size()).isEqualTo(5);
        assertThat(tableService.getState()).isEqualTo(GameState.ENDED);
    }

    @Test
    void testDistributeWinnings() {
        addPlayers();
        setActivePlayers();

        tableService.getPlayers().get(0).setInactive();
        tableService.pot = 10;

        tableService.distributeWinnings();

        assertThat(tableService.getPlayers().get(1).getCash()).isEqualTo(110);
        assertThat(tableService.pot).isEqualTo(0);
    }

    @Test
    void testPerformActionWhenGameEnded() {
        tableService.state = GameState.ENDED;
        tableService.performAction(Action.CHECK.getValue(),0);
    }
    @Test
    void testPerformCheck() {
        addPlayers();
        tableService.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        tableService.performAction(Action.CHECK.getValue(), 0);

        String output = baos.toString();

        assertThat(output).contains("Player Jack performed action: check, amount: 0");

    }

    @Test
    void testPerformFold() {
        addPlayers();
        tableService.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        tableService.performAction(Action.FOLD.getValue(), 0);

        String output = baos.toString();

        assertThat(output).contains("Player Jack performed action: fold, amount: 0");

    }

    @Test
    void testPerformCall() {
        addPlayers();
        tableService.start();
        tableService.performAction(Action.RAISE.getValue(), 10);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        tableService.performAction(Action.CALL.getValue(), 0);

        String output = baos.toString();

        assertThat(output).contains("Player Rose performed action: call, amount: 0");

    }

    @Test
    void testPerformRaise() {
        addPlayers();
        tableService.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        tableService.performAction(Action.RAISE.getValue(), 10);

        String output = baos.toString();

        assertThat(output).contains("Player Jack performed action: raise, amount: 10");

    }

    @Test
    void testPerformRaiseAfterFold() {
        addPlayers();
        tableService.start();
        tableService.performAction(Action.RAISE.getValue(), 10);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        tableService.performAction(Action.FOLD.getValue(), 0);

        String output = baos.toString();

        assertThat(output).contains("Player Rose performed action: fold, amount: 0");
        assertThat(tableService.state).isEqualTo(GameState.ENDED);
    }
    @Test
    void testGetPot() {
        tableService.pot = 10;
        assertThat(tableService.getPot()).isEqualTo(10);
    }

    @Test
    void testGetWinnerWhenGameNotEnded() {
        tableService.state = GameState.OPEN;
        assertThat(tableService.getWinner()).isEqualTo(Optional.empty());
    }

    @Test
    void testGetWinner() {
        addPlayers();
        setActivePlayers();

        tableService.getPlayers().get(0).setInactive();
        tableService.state = GameState.ENDED;

        assertThat(tableService.getWinner().get()).isEqualTo(tableService.getPlayers().get(1));
    }
}