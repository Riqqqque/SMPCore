package me.rique.smpcore.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackjackManagerTest {

    @Test
    void duplicateClientTapsCannotQueueMultipleActionsInOneTick() {
        assertTrue(BlackjackManager.acceptsGameAction(true, false));
        assertFalse(BlackjackManager.acceptsGameAction(true, true));
        assertFalse(BlackjackManager.acceptsGameAction(false, false));
    }

    @Test
    void splitPayoutStacksNeverExceedsMaterialStackSize() {
        List<Integer> amounts = BlackjackManager.splitStackAmounts(130, 64);

        assertEquals(List.of(64, 64, 2), amounts);
        assertTrue(amounts.stream().allMatch(amount -> amount <= 64));
        assertEquals(130, amounts.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void splitPayoutStacksIgnoresZeroAmount() {
        assertTrue(BlackjackManager.splitStackAmounts(0, 64).isEmpty());
    }

    @Test
    void deckContainsEveryUniqueCardExactlyOnce() {
        List<BlackjackManager.Card> deck = BlackjackManager.standardDeck();

        assertEquals(52, deck.size());
        assertEquals(52, Set.copyOf(deck).size());
    }

    @Test
    void faceCardsCountAsTen() {
        assertEquals(10, BlackjackManager.cardValue(card(BlackjackManager.Rank.TEN)));
        assertEquals(10, BlackjackManager.cardValue(card(BlackjackManager.Rank.JACK)));
        assertEquals(10, BlackjackManager.cardValue(card(BlackjackManager.Rank.QUEEN)));
        assertEquals(10, BlackjackManager.cardValue(card(BlackjackManager.Rank.KING)));
    }

    @Test
    void acesStayElevenUntilTheHandNeedsThemAsOne() {
        BlackjackManager.HandScore softSeventeen = BlackjackManager.scoreBaseValues(List.of(11, 6));
        BlackjackManager.HandScore hardSeventeen = BlackjackManager.scoreBaseValues(List.of(11, 6, 10));

        assertEquals(17, softSeventeen.total());
        assertTrue(softSeventeen.soft());
        assertEquals(List.of(11, 6), softSeventeen.countedValues());
        assertEquals(17, hardSeventeen.total());
        assertEquals(List.of(1, 6, 10), hardSeventeen.countedValues());
        assertFalse(hardSeventeen.soft());
    }

    @Test
    void multipleAcesAdjustIndividuallyAndStillCountEveryCard() {
        BlackjackManager.HandScore score = BlackjackManager.scoreBaseValues(List.of(11, 11, 11, 8));

        assertEquals(21, score.total());
        assertEquals(List.of(1, 1, 11, 8), score.countedValues());
        assertTrue(score.soft());
    }

    @Test
    void maximumLengthTwentyOneStillCountsAllElevenCards() {
        BlackjackManager.HandScore score = BlackjackManager.scoreBaseValues(List.of(
            11, 11, 11, 11,
            2, 2, 2, 2,
            3, 3, 3
        ));

        assertEquals(21, score.total());
        assertEquals(11, score.countedValues().size());
        assertFalse(score.soft());
    }

    @Test
    void naturalBlackjackRequiresExactlyTwoCards() {
        assertTrue(BlackjackManager.isBlackjack(List.of(
            card(BlackjackManager.Rank.ACE),
            card(BlackjackManager.Rank.KING)
        )));
        assertFalse(BlackjackManager.isBlackjack(List.of(
            card(BlackjackManager.Rank.SEVEN),
            card(BlackjackManager.Rank.SEVEN),
            card(BlackjackManager.Rank.SEVEN)
        )));
    }

    @Test
    void dealerHitsBelowSeventeenAndStandsOnSoftSeventeen() {
        assertTrue(BlackjackManager.dealerShouldHit(List.of(
            card(BlackjackManager.Rank.ACE),
            card(BlackjackManager.Rank.FIVE)
        )));
        assertFalse(BlackjackManager.dealerShouldHit(List.of(
            card(BlackjackManager.Rank.ACE),
            card(BlackjackManager.Rank.SIX)
        )));
    }

    @Test
    void handComparisonHandlesBustsWinsLossesAndPushes() {
        assertEquals(BlackjackManager.Result.LOSE, BlackjackManager.compareTotals(22, 23));
        assertEquals(BlackjackManager.Result.WIN, BlackjackManager.compareTotals(20, 22));
        assertEquals(BlackjackManager.Result.WIN, BlackjackManager.compareTotals(20, 19));
        assertEquals(BlackjackManager.Result.PUSH, BlackjackManager.compareTotals(18, 18));
        assertEquals(BlackjackManager.Result.LOSE, BlackjackManager.compareTotals(17, 18));
    }

    @Test
    void payoutsMatchTheMenuRules() {
        assertEquals(16, BlackjackManager.payoutAmount(BlackjackManager.Result.WIN, 8));
        assertEquals(8, BlackjackManager.payoutAmount(BlackjackManager.Result.PUSH, 8));
        assertEquals(0, BlackjackManager.payoutAmount(BlackjackManager.Result.LOSE, 8));
    }

    private static BlackjackManager.Card card(BlackjackManager.Rank rank) {
        return new BlackjackManager.Card(rank, BlackjackManager.Suit.SPADES);
    }
}
