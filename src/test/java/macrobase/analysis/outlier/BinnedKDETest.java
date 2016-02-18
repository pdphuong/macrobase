package macrobase.analysis.outlier;

import macrobase.datamodel.Datum;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class BinnedKDETest {

    @Test
    public void simpleTest() {
        KDE kde = new BinnedKDE(KDE.KernelType.EPANECHNIKOV_MULTIPLICATIVE, KDE.Bandwidth.OVERSMOOTHED, 1000);
        List<Datum> data = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            double[] sample = new double[1];
            sample[0] = i;
            data.add(new Datum(new ArrayList<>(), new ArrayRealVector(sample)));
        }

        kde.train(data);
        assertEquals(kde.score(data.get(0)), -0.005083, 1e-5);
        assertEquals(kde.score(data.get(50)), -0.010001, 1e-5);
        assertEquals(kde.score(data.get(data.size() - 1)), -0.005083, 1e-5);
    }
}
