package part1;



import java.util.ArrayList;
import java.util.List;

/**
 * Service that finds and prints prime numbers.
 */
//@Service
public class PrimeService {

    /**
     * Prints all prime numbers from 2 up to (and including) the given limit.
     *
     * @param limit the upper bound to check for primes (inclusive)
     */
    public void printPrimes(int limit) {
        for (int candidate = 2; candidate <= limit; candidate++) {
            if (isPrime(candidate)) {
                System.out.println(candidate);
            }
        }
    }

    /**
     * Returns all prime numbers from 2 up to (and including) the given limit.
     */
    public List<Integer> findPrimes(int limit) {
        List<Integer> primes = new ArrayList<>();
        for (int candidate = 2; candidate <= limit; candidate++) {
            if (isPrime(candidate)) {
                primes.add(candidate);
            }
        }
        return primes;
    }

    /**
     * Checks whether a single number is prime.
     */
    public boolean isPrime(int number) {
        if (number < 2) {
            return false;
        }
        for (int i = 2; (long) i * i <= number; i++) {
            if (number % i == 0) {
                return false;
            }
        }
        return true;
    }
}
