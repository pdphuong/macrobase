package edu.stanford.futuredata.macrobase.distributed.analysis.summary.aplinearDistributed;

import edu.stanford.futuredata.macrobase.analysis.summary.aplinear.APLExplanation;
import edu.stanford.futuredata.macrobase.analysis.summary.aplinear.APLExplanationResult;
import edu.stanford.futuredata.macrobase.analysis.summary.util.qualitymetrics.QualityMetric;
import edu.stanford.futuredata.macrobase.distributed.analysis.summary.DistributedBatchSummarizer;
import edu.stanford.futuredata.macrobase.distributed.analysis.summary.util.AttributeEncoderDistributed;
import edu.stanford.futuredata.macrobase.distributed.datamodel.DistributedDataFrame;
import org.apache.spark.api.java.JavaPairRDD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.util.List;
import java.util.Map;

/**
 * Generic summarizer superclass that can be customized with
 * different quality metrics and input sources. Subclasses are responsible
 * for converting from user-provided columns to the internal linear aggregates.
 */
public abstract class APLSummarizerDistributed extends DistributedBatchSummarizer {
    Logger log = LoggerFactory.getLogger("APLSummarizerDistributed");
    AttributeEncoderDistributed encoder;
    private APLExplanation explanation;
    protected int numPartitions = 1;
    private String countColumn = null;

    protected long numOutliers = 0;

    public abstract List<String> getAggregateNames();
    public abstract List<QualityMetric> getQualityMetricList();
    public abstract List<Double> getThresholds();
    public abstract JavaPairRDD<int[], double[]> getEncoded(
            JavaPairRDD<String[], double[]> partitionedDataFrame,
            double[] globalAggregates);

    APLSummarizerDistributed() {}

    public static JavaPairRDD<String[], double[]> transformDataFrame(DistributedDataFrame input,
                                                                     List<String> attributes, String outlierColumnName,
                                                                     String countColumnName, int numPartitions) {

        Map<String, Integer> nameToIndexMap = input.nameToIndexMap;

        JavaPairRDD<String[], double[]> mergedConsolidatedRDD = input.dataFrameRDD.mapToPair((Tuple2<String[], double[]> row) -> {
            String[] newAttributesCol = new String[attributes.size()];
            double[] newAggregatesCol = new double[2];
            for (int i = 0; i < attributes.size(); i++) {
                newAttributesCol[i] = row._1[nameToIndexMap.get(attributes.get(i))];
            }
            newAggregatesCol[0] = row._2[nameToIndexMap.get(outlierColumnName)];
            if (countColumnName == null)
                newAggregatesCol[1] = 1.0;
            else
                newAggregatesCol[1] = row._2[nameToIndexMap.get(countColumnName)];
            return new Tuple2<>(newAttributesCol, newAggregatesCol);
        });

        mergedConsolidatedRDD = mergedConsolidatedRDD.repartition(numPartitions);

        mergedConsolidatedRDD.cache();

        return mergedConsolidatedRDD;
    }

    public void process(DistributedDataFrame input) throws Exception {
        encoder = new AttributeEncoderDistributed();
        encoder.setColumnNames(attributes);
        long startTime = System.currentTimeMillis();
        JavaPairRDD<String[], double[]> partitionedDataFrame =
                transformDataFrame(input, attributes, outlierColumn, countColumn, numPartitions);

        double[] globalAggregates = partitionedDataFrame.reduce(
                (Tuple2<String[], double[]> first, Tuple2<String[], double[]> second) -> {
            final int numAggregates = first._2.length;
            double[] sumAggregates = new double[numAggregates];
            for (int i = 0; i < numAggregates; i++)
                sumAggregates[i] = first._2[i] + second._2[i];
            return new Tuple2<>(first._1, sumAggregates);
        })._2;

        JavaPairRDD<int[], double[]> encoded = getEncoded(partitionedDataFrame, globalAggregates);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Encoded in: {}", elapsed);
        log.info("Encoded Categories: {}", encoder.getNextKey() - 1);

        List<Double> thresholds = getThresholds();
        List<QualityMetric> qualityMetricList = getQualityMetricList();

        List<String> aggregateNames = getAggregateNames();
        List<APLExplanationResult> aplResults = APrioriLinearDistributed.explain(encoded,
                globalAggregates,
                encoder.getNextKey(),
                numPartitions,
                attributes.size(),
                qualityMetricList,
                thresholds
        );
        log.info("Number of results: {}", aplResults.size());

        explanation = new APLExplanation(
                encoder,
                Math.round(globalAggregates[1]),
                Math.round(globalAggregates[0]),
                aggregateNames,
                qualityMetricList,
                aplResults
        );
    }

    public APLExplanation getResults() {
        return explanation;
    }

    public void setNumPartitions(int numPartitions) {this.numPartitions = numPartitions;}

    public void setCountColumn(String countColumn) {
        this.countColumn = countColumn;
    }

}