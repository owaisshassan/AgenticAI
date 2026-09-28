package part1;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UntestedServiceTest {

    private final UntestedService service = new UntestedService();

    // ---- isOdd tests ----

    @Test
    void isOdd_returnsTrueForOddNumber() {
        assertTrue(service.isOdd(1));
        assertTrue(service.isOdd(3));
        assertTrue(service.isOdd(-1));
        assertTrue(service.isOdd(101));
    }

    @Test
    void isOdd_returnsFalseForEvenNumber() {
        assertFalse(service.isOdd(0));
        assertFalse(service.isOdd(2));
        assertFalse(service.isOdd(-2));
        assertFalse(service.isOdd(100));
    }

    // ---- findOddNumbers tests ----

    @Test
    void findOddNumbers_returnsOddNumbersUpToLimit() {
        List<Integer> result = service.findOddNumbers(10);
        assertEquals(List.of(1, 3, 5, 7, 9), result);
    }

    @Test
    void findOddNumbers_withLimitOne_returnsSingleOddNumber() {
        List<Integer> result = service.findOddNumbers(1);
        assertEquals(List.of(1), result);
    }

    @Test
    void findOddNumbers_withZeroLimit_returnsEmptyList() {
        List<Integer> result = service.findOddNumbers(0);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOddNumbers_withNegativeLimit_returnsEmptyList() {
        List<Integer> result = service.findOddNumbers(-5);
        assertTrue(result.isEmpty());
    }

    @Test
    void findOddNumbers_withLimitTwo_returnsOnlyOne() {
        List<Integer> result = service.findOddNumbers(2);
        assertEquals(List.of(1), result);
    }

    // ---- printOddNumbers tests ----

    @Test
    void printOddNumbers_doesNotThrowForPositiveLimit() {
        assertDoesNotThrow(() -> service.printOddNumbers(10));
    }

    @Test
    void printOddNumbers_doesNotThrowForZeroLimit() {
        assertDoesNotThrow(() -> service.printOddNumbers(0));
    }

    @Test
    void printOddNumbers_doesNotThrowForNegativeLimit() {
        assertDoesNotThrow(() -> service.printOddNumbers(-5));
    }
}