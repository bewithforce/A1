import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class FactorialsLimit {
    private final List<BigInteger> factorials;

    FactorialsLimit() {
        factorials = new ArrayList<>(30);
        var a = new BigInteger("1");
        factorials.add(a);
        factorials.add(a);
    }

    public static void main(String[] args) throws Exception {
        var task = new FactorialsLimit();
        System.out.println(String.format("%010d", Integer.parseInt("123")));
        for (int i = 1; i <= 13; i++) {
            System.out.println(task.magic(i).toString());
        }
    }

    private BigInteger factorialN(int n) throws Exception {
        if (n < 0) {
            throw new Exception("negative number");
        }
        int size = factorials.size();
        if (n < size) {
            return factorials.get(n);
        }
        BigInteger base = factorials.get(size - 1);
        for (int i = size; i <= n; i++) {
            base = base.multiply(new BigInteger(String.valueOf(i)));
            factorials.add(base);
        }

        return base;
    }

    public BigDecimal magic(int n) throws Exception {
        var denominator = factorialN(n);
        var numerator = sumOfFirstNFactorials(n);

        var divAndRem = numerator.divideAndRemainder(denominator);
        var integerPart = divAndRem[0];
        var remainderPart = divAndRem[1];

        int fasterRem = 0;

        for (int i = 0; i < 6; i++) {
            fasterRem *= 10;
            var temp = remainderPart.multiply(BigInteger.TEN).divideAndRemainder(denominator);
            remainderPart = temp[1];
            fasterRem += temp[0].intValue();
            /*
            if (remainderPart.equals(BigInteger.ZERO)) {
                break;
            }*/
        }
        var temp = remainderPart.multiply(BigInteger.TEN).divideAndRemainder(denominator);
        if (temp[0].intValue() >= 5) {
            fasterRem++;
        }

        String result = integerPart.toString() + '.' + String.format("%06d", fasterRem);
        return new BigDecimal(result);
    }

    private BigInteger sumOfFirstNFactorials(int n) throws Exception {
        if (n > factorials.size()) {
            factorialN(n);
        }
        var sum = BigInteger.ZERO;
        for (int i = 1; i <= n; i++) {
            sum = sum.add(factorials.get(i));
        }

        return sum;
    }

}
