package com.yahoo.omid.transaction;

import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.omid.transaction.TTable;
import com.yahoo.omid.transaction.Transaction;
import com.yahoo.omid.transaction.TransactionManager;

public class TestAbortTransaction extends OmidTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(TestAbortTransaction.class);


    @Test 
    public void runTestInterleaveScan() throws Exception {
        try {
            TransactionManager tm = newTransactionManager();
            TTable tt = new TTable(hbaseConf, TEST_TABLE);
         
            Transaction t1 = tm.begin();
            LOG.info("Transaction created " + t1);
         
            byte[] fam = Bytes.toBytes(TEST_FAMILY);
            byte[] col = Bytes.toBytes("testdata");
            byte[] data1 = Bytes.toBytes("testWrite-1");
            byte[] data2 = Bytes.toBytes("testWrite-2");
         
            byte[] startrow = Bytes.toBytes("test-scan" + 0);
            byte[] stoprow = Bytes.toBytes("test-scan" + 9);
            byte[] modrow = Bytes.toBytes("test-scan" + 3);
            for (int i = 0; i < 10; i++) {
                byte[] row = Bytes.toBytes("test-scan" + i);
            
                Put p = new Put(row);
                p.add(fam, col, data1);
                tt.put(t1, p);
            }
            tm.commit(t1);

            Transaction t2 = tm.begin();
            Put p = new Put(modrow);
            p.add(fam, col, data2);
            tt.put(t2, p);         
         
            int modifiedrows = 0;
            ResultScanner rs = tt.getScanner(t2, new Scan().setStartRow(startrow).setStopRow(stoprow).addColumn(fam, col));
            Result r = rs.next();
            while (r != null) {
                if (Bytes.equals(data2, r.getValue(fam, col))) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Modified :" + Bytes.toString(r.getRow()));
                    }
                    modifiedrows++;
                }
            
                r = rs.next();
            }
         
            assertTrue("Expected 1 row modified, but " + modifiedrows + " are.", 
                       modifiedrows == 1);
            tm.rollback(t2);
         
            Transaction tscan = tm.begin();
            rs = tt.getScanner(tscan, new Scan().setStartRow(startrow).setStopRow(stoprow).addColumn(fam, col));
            r = rs.next();
            while (r != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Scan1 :" + Bytes.toString(r.getRow()) + " => " + Bytes.toString(r.getValue(fam, col)));
                }

                assertTrue("Unexpected value for SI scan " + tscan + ": " + Bytes.toString(r.getValue(fam, col)),
                           Bytes.equals(data1, r.getValue(fam, col)));
                r = rs.next();
            }

        } catch (Exception e) {
            LOG.error("Exception occurred", e);
            throw e;
        }
    }
}
