package part1;

import java.util.ArrayList;
import java.util.List;

public class TestService {

    public void printOddNumbers(int limit) {
        for (int number = 1; number <= limit; number++) {
            if (isOdd(number)) {
                System.out.println(number);
            }
        }
    }

    public List<Integer> findOddNumbers(int limit) {
        List<Integer> odds = new ArrayList<>();
        for (int number = 1; number <= limit; number++) {
            if (isOdd(number)) {
                odds.add(number);
            }
        }
        return odds;
    }

    /**
     * Checks whether a single number is odd.
     */
    public boolean isOdd(int number) {
        return number % 2 != 0;
    }
}
