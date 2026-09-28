package part1;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestServiceTest {

    private final TestService testService = new TestService();

    // ---------- isOdd ----------

    @Test
    void isOdd_returnsTrueForPositiveOddNumber() {
        assertTrue(testService.isOdd(3));
    }

    @Test
    void isOdd_returnsFalseForPositiveEvenNumber() {
        assertFalse(testService.isOdd(4));
    }

    @Test
    void isOdd_returnsFalseForZero() {
        assertFalse(testService.isOdd(0));
    }

    @Test
    void isOdd_returnsTrueForNegativeOddNumber() {
        assertTrue(testService.isOdd(-3));
    }

    @Test
    void isOdd_returnsFalseForNegativeEvenNumber() {
        assertFalse(testService.isOdd(-4));
    }

    // ---------- findOddNumbers ----------

    @Test
    void findOddNumbers_returnsCorrectListForPositiveLimit() {
        List<Integer> result = testService.findOddNumbers(10);
        assertEquals(List.of(1, 3, 5, 7, 9), result);
    }

    @Test
    void findOddNumbers_returnsSingleElementForLimitOne() {
        List<Integer> result = testService.findOddNumbers(1);
        assertEquals(List.of(1), result);
    }

    @Test
    void findOddNumbers_returnsEmptyListForLimitZero() {
        List<Integer> result = testService.findOddNumbers(0);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOddNumbers_returnsEmptyListForNegativeLimit() {
        List<Integer> result = testService.findOddNumbers(-5);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOddNumbers_returnsCorrectListForLargerLimit() {
        List<Integer> result = testService.findOddNumbers(20);
        assertEquals(20 / 2, result.size());
        assertEquals(1, result.get(0));
        assertEquals(19, result.get(result.size() - 1));
    }

    // ---------- printOddNumbers ----------

    @Test
    void printOddNumbers_doesNotThrowForPositiveLimit() {
        assertDoesNotThrow(() -> testService.printOddNumbers(10));
    }

    @Test
    void printOddNumbers_doesNotThrowForZeroLimit() {
        assertDoesNotThrow(() -> testService.printOddNumbers(0));
    }

    @Test
    void printOddNumbers_doesNotThrowForNegativeLimit() {
        assertDoesNotThrow(() -> testService.printOddNumbers(-5));
    }
}