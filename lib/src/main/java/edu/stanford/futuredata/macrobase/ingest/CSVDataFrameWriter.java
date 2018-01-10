package edu.stanford.futuredata.macrobase.ingest;

import edu.stanford.futuredata.macrobase.datamodel.DataFrame;
import edu.stanford.futuredata.macrobase.datamodel.Row;
import java.io.IOException;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class CSVDataFrameWriter {

    private final CSVFormat format;

    public CSVDataFrameWriter() {
        format = CSVFormat.MYSQL.withNullString("null");
    }

    public CSVDataFrameWriter(final String fieldDelimiter, final String lineDelimiter) {
        this.format = CSVFormat.MYSQL.withNullString("null").withDelimiter(fieldDelimiter.charAt(0))
            .withRecordSeparator(lineDelimiter);
    }

    public void writeToStream(DataFrame df, Appendable out) throws IOException {
        String[] columnNames = df.getSchema().getColumnNames().toArray(new String[0]);
        CSVPrinter printer = format.withHeader(columnNames).print(out);

        List<Row> rows = df.getRows();
        for (Row curRow : rows) {
          List<Object> rowValues = curRow.getVals();
          printer.printRecord(rowValues);
        }
        printer.close();
    }
}
