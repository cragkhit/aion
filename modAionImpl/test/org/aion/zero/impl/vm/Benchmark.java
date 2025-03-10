/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.zero.impl.types.MiningBlock;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ECKeyFac.ECKeyType;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.base.db.RepositoryCache;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.slf4j.Logger;

public class Benchmark {

    private static MiningBlock block = createDummyBlock();
    private static AionRepositoryImpl db = AionRepositoryImpl.inst();
    private static RepositoryCache repo = db.startTracking();

    private static ECKey key;
    private static AionAddress owner;
    private static AionAddress contract;

    private static List<byte[]> recipients = new ArrayList<>();

    private static long timePrepare;
    private static long timeSignTransactions;
    private static long timeValidateTransactions;
    private static long timeExecuteTransactions;
    private static long timeFlush;

    private static Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.VM.name());

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    private static void prepare() throws Exception {
        AvmTestConfig.supportOnlyAvmVersion1();

        long t1 = System.currentTimeMillis();

        // create owner account
        ECKeyFac.setType(ECKeyType.ED25519);
        key = ECKeyFac.inst().create();
        owner = new AionAddress(key.getAddress());
        repo.createAccount(owner);
        repo.addBalance(owner, BigInteger.valueOf(1_000_000_000L));

        // create transaction
        byte[] deployer =
                ContractUtils.getContractDeployer("BenchmarkERC20.sol", "FixedSupplyToken");
        byte[] nonce = BigInteger.ZERO.toByteArray();
        AionAddress to = null;
        byte[] value = BigInteger.ZERO.toByteArray();
        long nrg = 1_000_000L;
        long nrgPrice = 10_000_000_000L;
        AionTransaction tx = AionTransaction.create(
                key,
                nonce,
                to,
                value,
                deployer,
                nrg,
                nrgPrice,
                TransactionTypes.DEFAULT, null);

        // save contract address
        contract = TxUtil.calculateContractAddress(tx);

        // deploy contract
        AionTxExecSummary summary = executeTransaction(tx);
        assertFalse(summary.isFailed());

        long t2 = System.currentTimeMillis();
        timePrepare = t2 - t1;
    }

    private static List<AionTransaction> signTransactions(int num) {
        long t1 = System.currentTimeMillis();
        List<AionTransaction> list = new ArrayList<>();

        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < num; i++) {
            byte[] recipient = RandomUtils.nextBytes(AionAddress.LENGTH);
            recipients.add(recipient);

            // transfer token to random people
            byte[] nonce = BigInteger.valueOf(ownerNonce + i).toByteArray();
            AionAddress to = contract;
            byte[] value = BigInteger.ZERO.toByteArray();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("fbb001d6" + "000000000000000000000000"),
                            recipient,
                            BigInteger.ONE.toByteArray());
            long nrg = 1_000_000L;
            long nrgPrice = 10_000_000_000L;
            AionTransaction tx = AionTransaction.create(
                    key,
                    nonce,
                    to,
                    value,
                    data,
                    nrg,
                    nrgPrice,
                    TransactionTypes.DEFAULT, null);

            list.add(tx);
        }

        long t2 = System.currentTimeMillis();
        timeSignTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> validateTransactions(List<AionTransaction> txs) {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            assertNotNull(tx.getTransactionHash());
            assertEquals(32, tx.getTransactionHash().length);
            assertNotNull(tx.getValue());
            assertEquals(16, tx.getValue().length);
            assertNotNull(tx.getData());
            assertNotNull(tx.getSenderAddress());
            assertNotNull(tx.getDestinationAddress());
            assertNotNull(tx.getNonce());
            assertEquals(16, tx.getNonce().length);
            assertTrue(tx.getEnergyLimit() > 0);
            assertTrue(tx.getEnergyPrice() > 0);
            assertTrue(SignatureFac.verify(tx.getTransactionHashWithoutSignature(), tx.getSignature()));
        }

        long t2 = System.currentTimeMillis();
        timeValidateTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> executeTransactions(List<AionTransaction> txs)
            throws VmFatalException {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            AionTxExecSummary summary = executeTransaction(tx);
            assertFalse(summary.isFailed());

            list.add(summary.getReceipt());
        }

        long t2 = System.currentTimeMillis();
        timeExecuteTransactions = t2 - t1;

        return list;
    }

    private static void flush() {
        long t1 = System.currentTimeMillis();

        repo.flushTo(db, true);
        db.flush();

        long t2 = System.currentTimeMillis();
        timeFlush = t2 - t1;
    }

    private static void verifyState() throws VmFatalException {
        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < recipients.size(); i++) {
            byte[] nonce = BigInteger.valueOf(ownerNonce + i).toByteArray();
            AionAddress to = contract;
            byte[] value = BigInteger.ZERO.toByteArray();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("70a08231" + "000000000000000000000000"), recipients.get(i));
            long nrg = 1_000_000L;
            long nrgPrice = 10_000_000_000L;
            AionTransaction tx = AionTransaction.create(
                    key,
                    nonce,
                    to,
                    value,
                    data,
                    nrg,
                    nrgPrice,
                    TransactionTypes.DEFAULT, null);

            AionTxExecSummary summary = executeTransaction(tx);
            assertFalse(summary.isFailed());

            assertEquals(
                    1, new DataWord(summary.getReceipt().getTransactionOutput()).longValue());
        }
    }

    public static void main(String args[]) throws Exception {
        int n = 10000;
        prepare();
        List<AionTransaction> list = signTransactions(n);
        validateTransactions(list);
        executeTransactions(list);
        flush();
        verifyState();

        System.out.println("==========================================");
        System.out.println("Benchmark (ERC20 transfer): " + n + " txs");
        System.out.println("==========================================");
        System.out.println("prepare               : " + timePrepare + " ms");
        System.out.println("sign_transactions     : " + timeSignTransactions + " ms");
        System.out.println("validate_transactions : " + timeValidateTransactions + " ms");
        System.out.println("execute_transactions  : " + timeExecuteTransactions + " ms");
        System.out.println("flush                 : " + timeFlush + " ms");
    }

    private static MiningBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWord(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        // TODO: set a dummy limit of 5000000 for now
        return new MiningBlock(
                parentHash,
                new AionAddress(coinbase),
                logsBloom,
                difficulty,
                number,
                timestamp,
                extraData,
                nonce,
                receiptsRoot,
                transactionsRoot,
                stateRoot,
                transactionsList,
                solutions,
                0,
                5000000);
    }

    private static AionTxExecSummary executeTransaction(AionTransaction transaction)
            throws VmFatalException {
        return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block.getDifficulty(),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                transaction,
                repo,
                false,
                true,
                false,
                false,
                LOGGER,
                BlockCachingContext.PENDING,
                block.getNumber() - 1,
                false,
                false);
    }
}
