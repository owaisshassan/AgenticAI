package part1;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimeServiceTest {

    private final PrimeService primeService = new PrimeService();

    private PrintStream originalOut;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    // ---------- isPrime tests ----------

    @Test
    void isPrime_returnsFalseForNumbersLessThanTwo() {
        assertFalse(primeService.isPrime(1));
        assertFalse(primeService.isPrime(0));
        assertFalse(primeService.isPrime(-5));
        assertFalse(primeService.isPrime(Integer.MIN_VALUE));
    }

    @Test
    void isPrime_returnsTrueForTwo() {
        assertTrue(primeService.isPrime(2));
    }

    @Test
    void isPrime_returnsTrueForSmallPrimes() {
        assertTrue(primeService.isPrime(3));
        assertTrue(primeService.isPrime(5));
        assertTrue(primeService.isPrime(7));
        assertTrue(primeService.isPrime(13));
    }

    @Test
    void isPrime_returnsFalseForCompositeNumbers() {
        assertFalse(primeService.isPrime(4));
        assertFalse(primeService.isPrime(6));
        assertFalse(primeService.isPrime(9));
        assertFalse(primeService.isPrime(100));
    }

    @Test
    void isPrime_returnsTrueForLargerPrime() {
        assertTrue(primeService.isPrime(97));
    }

    @Test
    void isPrime_returnsFalseForLargerComposite() {
        assertFalse(primeService.isPrime(91)); // 7 * 13
    }

    // ---------- findPrimes tests ----------

    @Test
    void findPrimes_happyPath() {
        List<Integer> primes = primeService.findPrimes(20);
        assertEquals(Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19), primes);
    }

    @Test
    void findPrimes_limitLessThanTwo_returnsEmptyList() {
        assertEquals(Collections.emptyList(), primeService.findPrimes(1));
        assertEquals(Collections.emptyList(), primeService.findPrimes(0));
        assertEquals(Collections.emptyList(), primeService.findPrimes(-10));
    }

    @Test
    void findPrimes_limitIsTwo_returnsOnlyTwo() {
        assertEquals(Collections.singletonList(2), primeService.findPrimes(2));
    }

    @Test
    void findPrimes_limitIsThree_returnsTwoAndThree() {
        assertEquals(Arrays.asList(2, 3), primeService.findPrimes(3));
    }

    // ---------- printPrimes tests ----------

    @Test
    void printPrimes_happyPath_printsExpectedPrimes() {
        primeService.printPrimes(10);
        String expected = "2" + System.lineSeparator()
                + "3" + System.lineSeparator()
                + "5" + System.lineSeparator()
                + "7" + System.lineSeparator();
        assertEquals(expected, outputStream.toString());
    }

    @Test
    void printPrimes_limitLessThanTwo_printsNothing() {
        primeService.printPrimes(1);
        assertEquals("", outputStream.toString());

        outputStream.reset();
        primeService.printPrimes(0);
        assertEquals("", outputStream.toString());

        outputStream.reset();
        primeService.printPrimes(-5);
        assertEquals("", outputStream.toString());
    }

    @Test
    void printPrimes_limitIsTwo_printsOnlyTwo() {
        primeService.printPrimes(2);
        String expected = "2" + System.lineSeparator();
        assertEquals(expected, outputStream.toString());
    }
}