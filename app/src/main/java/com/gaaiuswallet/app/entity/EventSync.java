package com.gaaiuswallet.app.entity;

import android.util.Pair;

import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.repository.EthereumNetworkBase;
import com.gaaiuswallet.app.repository.TokensRealmSource;
import com.gaaiuswallet.app.repository.entity.RealmAuxData;
import com.gaaiuswallet.app.repository.entity.RealmTransfer;
import com.gaaiuswallet.app.service.TransactionsService;

import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;
import timber.log.Timber;

/**
 * Created by JB on 23/04/2022.
 */
public class EventSync
{
    public static final long BLOCK_SEARCH_INTERVAL = 100000L;
    public static final long POLYGON_BLOCK_SEARCH_INTERVAL = 3000L;
    public static final long OKX_BLOCK_SEARCH_INTERVAL = 2000L;

    private static final String TAG = "EVENT_SYNC";
    private static final boolean EVENT_SYNC_DEBUGGING = false;

    private final Token token;

    private static final HashSet<Long> batchProcessingError = new HashSet<>();

    public EventSync(Token token)
    {
        this.token = token;
    }

    //Log fetch strategy for NFT:
    //1. Try entire block range (1 -> LATEST). For most accounts, this will work correctly and will find all events (< 3500 for Polygon, < 10000 other chains)
    //1a.   If successful, record newest event block# and use that as the starting point for next scans as per step 7.
    //2. If it errors with "Too many events", try a smaller range but ending with LATEST. Track the range and go to 3.
    //3.   If still "Too many events", reduce range by intervals, and do step 3. again (record the upper bound)
    //4.   when we start receiving events, process and move downwards
    //5.     scan again, if "Too many events" reduce range further and go back to 4.
    //6.     if no events found, or less than full widen scan again and continue to 1
    //7.     Start scanning from the upperbound to LATEST. Update upper bound if any event found.

    public SyncDef getSyncDef(Realm realm)
    {
        BigInteger currentBlock = TransactionsService.getCurrentBlock(token.tokenInfo.chainId);
        EventSyncState syncState = getCurrentTokenSyncState(realm);
        BigInteger lastBlockRead = BigInteger.valueOf(getLastEventRead(realm));
        long readBlockSize = getCurrentEventBlockSize(realm);
        BigInteger eventReadStartBlock;
        BigInteger eventReadEndBlock;

        if (currentBlock.equals(BigInteger.ZERO)) return null;

        boolean upwardSync = false;

        switch (syncState)
        {
            default:
            case DOWNWARD_SYNC_START: //Start event sync, optimistically try the whole current event range from 1 -> LATEST
                eventReadStartBlock = BigInteger.ONE;
                eventReadEndBlock = BigInteger.valueOf(-1L);
                if (EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId))
                {
                    syncState = EventSyncState.UPWARD_SYNC;
                    eventReadStartBlock = currentBlock.subtract(EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).multiply(BigInteger.valueOf(3)));
                    EVENT_DEBUG("Init Sync for restricted block RPC");
                }
                else
                {
                    //write the start point here
                    writeStartSyncBlock(realm, currentBlock.longValue());
                }
                break;
            case DOWNWARD_SYNC: //we needed to slow down the sync
                eventReadStartBlock = lastBlockRead.subtract(BigInteger.valueOf(readBlockSize));
                eventReadEndBlock = lastBlockRead;
                if (eventReadStartBlock.compareTo(BigInteger.ZERO) <= 0)
                {
                    eventReadStartBlock = BigInteger.ONE;
                    syncState = EventSyncState.DOWNWARD_SYNC_COMPLETE;
                }
                break;
            case UPWARD_SYNC_MAX: //we are syncing from the point we started the downward sync
                upwardSync = true;
                if (EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId) && upwardSyncStateLost(lastBlockRead, currentBlock))
                {
                    syncState = EventSyncState.UPWARD_SYNC;
                    EVENT_DEBUG("Switch back to sync scan");
                }

                eventReadStartBlock = lastBlockRead;
                eventReadEndBlock = BigInteger.valueOf(-1L);
                break;
            case UPWARD_SYNC: //we encountered upward sync issues
                upwardSync = true;
                eventReadStartBlock = lastBlockRead;
                if (upwardSyncComplete(eventReadStartBlock, currentBlock)) //detect completion of upward sync and switch to sync_max
                {
                    eventReadEndBlock = BigInteger.valueOf(-1L);
                    syncState = EventSyncState.UPWARD_SYNC_MAX;
                    EVENT_DEBUG("Sync complete");
                }
                else
                {
                    eventReadEndBlock = lastBlockRead.add(BigInteger.valueOf(readBlockSize));
                }
                break;
        }

        // Finally adjust the event end read if required. This is placed outside the switch because it should affect
        // a few different paths
        eventReadEndBlock = adjustForLimitedBlockSize(eventReadStartBlock, eventReadEndBlock, currentBlock);

        // detect edge condition - it's highly unlikely but acts as a stopper in case of unexpected results
        // This edge condition is where the start block read is greater than the current block.
        if (eventReadStartBlock.compareTo(currentBlock) >= 0)
        {
            eventReadStartBlock = currentBlock.subtract(BigInteger.ONE);
            eventReadEndBlock = BigInteger.valueOf(-1L);
            syncState = EventSyncState.UPWARD_SYNC_MAX;
        }

        return new SyncDef(eventReadStartBlock, eventReadEndBlock, syncState, upwardSync);
    }

    private boolean upwardSyncStateLost(BigInteger lastBlockRead, BigInteger currentBlock)
    {
        return currentBlock.subtract(lastBlockRead).compareTo(EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId)) >= 0;
    }

    private boolean upwardSyncComplete(BigInteger eventReadStartBlock, BigInteger currentBlock)
    {
        BigInteger maxBlockRead = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).subtract(BigInteger.ONE);
        BigInteger diff = currentBlock.subtract(eventReadStartBlock);

        return diff.compareTo(maxBlockRead) < 0;
    }

    private BigInteger adjustForLimitedBlockSize(BigInteger eventReadStartBlock, BigInteger eventReadEndBlock, BigInteger currentBlock)
    {
        if (EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId))
        {
            BigInteger maxBlockRead = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId);

            long diff = currentBlock.subtract(eventReadStartBlock).longValue();

            if (diff >= maxBlockRead.longValue())
            {
                return eventReadStartBlock.add(maxBlockRead).subtract(BigInteger.ONE);
            }
        }

        return eventReadEndBlock;
    }

    public boolean handleEthLogError(Response.Error error, DefaultBlockParameter startBlock, DefaultBlockParameter endBlock, SyncDef sync, Realm realm)
    {
        if (error.getMessage().toLowerCase(Locale.ROOT).contains("block")) //trigger on block range error
        {
            long newStartBlock;
            long newEndBlock;
            long blockSize;
            BigInteger startBlockVal = Numeric.toBigInt(startBlock.getValue());
            EventSyncState state;
            if (sync.upwardSync)
            {
                if (endBlock.getValue().equalsIgnoreCase("latest"))
                {
                    newStartBlock = startBlockVal.longValue();
                    newEndBlock = newStartBlock + EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
                    blockSize = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
                }
                else
                {
                    BigInteger endBlockVal = Numeric.toBigInt(endBlock.getValue());
                    //in the process of scanning up, encountered too many events for this interval, so reduce interval
                    long currentBlockScan = endBlockVal.subtract(startBlockVal).longValue();
                    newStartBlock = startBlockVal.longValue();
                    newEndBlock = newStartBlock + currentBlockScan / 2;
                    blockSize = newEndBlock - newStartBlock;
                }

                state = EventSyncState.UPWARD_SYNC;
            }
            else
            {
                //Too many logs, reduce fetch size
                //Measure interval
                if (endBlock.getValue().equalsIgnoreCase("latest"))
                {
                    BigInteger currentBlock = TransactionsService.getCurrentBlock(token.tokenInfo.chainId);
                    blockSize = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
                    newEndBlock = currentBlock.longValue();
                }
                else
                {
                    //in the process of scanning down, encountered too many events for this interval, so reduce interval
                    newEndBlock = Numeric.toBigInt(endBlock.getValue()).longValue();
                    newStartBlock = reduceBlockSearch(newEndBlock, startBlockVal);
                    blockSize = newEndBlock - newStartBlock;
                }

                state = EventSyncState.DOWNWARD_SYNC;
            }

            updateEventReads(realm, newEndBlock, blockSize, state);

            //now call again
            return true;
        }
        else
        {
            //try again later with same settings, could be connection fault.
            Timber.w("Event fetch error: %s", error.getMessage());
            return false;
        }
    }

    private long reduceBlockSearch(long currentBlock, BigInteger startBlock)
    {
        if (startBlock.compareTo(BigInteger.ONE) == 0)
        {
            //initial search, apply full limit
            return currentBlock - EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
        }
        else
        {
            //half existing interval, and try again
            return currentBlock - (currentBlock - startBlock.longValue())/2;
        }
    }

    private long getCurrentEventBlockSize(Realm instance)
    {
        RealmAuxData rd = instance.where(RealmAuxData.class)
                .equalTo("instanceKey", TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress()))
                .findFirst();

        if (rd == null)
        {
            return EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
        }
        else
        {
            return Math.min(rd.getResultReceivedTime(), EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue());
        }
    }

    protected EventSyncState getCurrentTokenSyncState(Realm instance)
    {
        RealmAuxData rd = instance.where(RealmAuxData.class)
                .equalTo("instanceKey", TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress()))
                .findFirst();

        if (rd == null)
        {
            writeCurrentTokenSyncState(instance, EventSyncState.DOWNWARD_SYNC_START);
            return EventSyncState.DOWNWARD_SYNC_START;
        }
        else
        {
            int state = rd.getTokenId().intValue();
            if (state >= EventSyncState.DOWNWARD_SYNC_START.ordinal() && state < EventSyncState.TOP_LIMIT.ordinal())
            {
                EVENT_DEBUG("Read State: " + EventSyncState.values()[state]);
                return EventSyncState.values()[state];
            }
            else
            {
                writeCurrentTokenSyncState(instance, EventSyncState.DOWNWARD_SYNC_START);
                return EventSyncState.DOWNWARD_SYNC_START;
            }
        }
    }

    public void writeCurrentTokenSyncState(Realm realm, EventSyncState newState)
    {
        if (realm == null) return;
        realm.executeTransaction(r -> {
            String key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress());
            RealmAuxData rd = r.where(RealmAuxData.class)
                    .equalTo("instanceKey", key)
                    .findFirst();

            if (rd == null)
            {
                rd = r.createObject(RealmAuxData.class, key); //create asset in realm
            }

            rd.setTokenId(String.valueOf(newState.ordinal()));
            r.insertOrUpdate(rd);
        });
    }

    protected long getLastEventRead(Realm instance)
    {
        RealmAuxData rd = instance.where(RealmAuxData.class)
                .equalTo("instanceKey", TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress()))
                .findFirst();

        if (rd == null)
        {
            return -1L;
        }
        else
        {
            EVENT_DEBUG("ReadEventSync: " + rd.getResultTime());
            return rd.getResultTime();
        }
    }

    private long getSyncStart(Realm instance)
    {
        RealmAuxData rd = instance.where(RealmAuxData.class)
                .equalTo("instanceKey", TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress()))
                .findFirst();

        if (rd == null)
        {
            return TransactionsService.getCurrentBlock(token.tokenInfo.chainId).longValue();
        }
        else
        {
            return Long.parseLong(rd.getFunctionId());
        }
    }

    protected void writeStartSyncBlock(Realm realm, long currentBlock)
    {
        if (realm == null) return;
        realm.executeTransaction(r -> {
            String key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress());
            RealmAuxData rd = r.where(RealmAuxData.class)
                    .equalTo("instanceKey", key)
                    .findFirst();

            if (rd == null)
            {
                rd = r.createObject(RealmAuxData.class, key); //create asset in realm
            }

            rd.setFunctionId(String.valueOf(currentBlock));
            r.insertOrUpdate(rd);
        });
    }

    public void updateEventReads(Realm realm, SyncDef sync, BigInteger currentBlock, int evReads)
    {
        switch (sync.state)
        {
            //update current read-from block (either we completed a successful
            case UPWARD_SYNC_MAX: //completed read from last sync to latest
            case DOWNWARD_SYNC_START: //completed read from 1 to latest
                sync = new SyncDef(BigInteger.ONE, currentBlock,
                        EventSyncState.UPWARD_SYNC_MAX, true);
                break;
            case DOWNWARD_SYNC_COMPLETE: //finished the event read
                //next time, start where we originally synced from
                updateEventReads(realm, getSyncStart(realm), EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue(), EventSyncState.UPWARD_SYNC_MAX);
                return;
            case DOWNWARD_SYNC: //successful intermediate downward sync
            case UPWARD_SYNC: //successful intermediate upward sync
            case TOP_LIMIT:
                break;
        }

        updateEventReads(realm, sync.eventReadEndBlock.longValue(), calcNewIntervalSize(sync, evReads), sync.state);
    }

    public void resetEventReads(Realm realm)
    {
        updateEventReads(realm, 0, 0, EventSyncState.DOWNWARD_SYNC_START);
    }

    private void updateEventReads(Realm realm, long lastRead, long readInterval, EventSyncState state)
    {
        if (realm == null) return;
        realm.executeTransaction(r -> {
            String key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress());
            RealmAuxData rd = r.where(RealmAuxData.class)
                    .equalTo("instanceKey", key)
                    .findFirst();

            if (rd == null)
            {
                rd = r.createObject(RealmAuxData.class, key); //create asset in realm
            }

            rd.setResultTime(lastRead);
            rd.setResultReceivedTime(readInterval);
            rd.setTokenId(String.valueOf(state.ordinal()));

            EVENT_DEBUG("WriteState: " + state + " " + lastRead);

            r.insertOrUpdate(rd);
        });
    }

    // If we're syncing downwards, work out what event block size we should read next
    private long calcNewIntervalSize(SyncDef sync, int evReads)
    {
        if (sync.upwardSync) return EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
        long endBlock = sync.eventReadEndBlock.longValue() == -1 ? TransactionsService.getCurrentBlock(token.tokenInfo.chainId).longValue()
                : sync.eventReadEndBlock.longValue();
        long currentReadSize = endBlock - sync.eventReadStartBlock.longValue();
        long maxLogReads = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
        // under the log limit?
        if (evReads == 0)
        {
            currentReadSize *= 4;
        }
        else if (evReads < 1000)
        {
            //increase block read size
            currentReadSize *= 2;
        }
        else if ((maxLogReads - evReads) > maxLogReads*0.25)
        {
            currentReadSize += EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).longValue();
        }

        return currentReadSize;
    }

    /***
     * Event Handling
     *
     * TODO: batch up catch-up calls
     */

    public Pair<Integer, Pair<HashSet<BigInteger>, HashSet<BigInteger>>> processTransferEvents(Web3j web3j, Event transferEvent, DefaultBlockParameter startBlock,
                                                                       DefaultBlockParameter endBlock, Realm realm)
            throws IOException, LogOverflowException
    {
        HashSet<String> txHashes = new HashSet<>();
        EthFilter receiveFilter = token.getReceiveBalanceFilter(transferEvent, startBlock, endBlock);
        EthFilter sendFilter    = token.getSendBalanceFilter(transferEvent, startBlock, endBlock);

        Pair<EthLog, EthLog> ethLogs = getTxLogs(web3j, receiveFilter, sendFilter);

        EthLog receiveLogs = ethLogs.first;
        EthLog sendLogs = ethLogs.second;

        int eventCount = receiveLogs.getLogs().size();

        HashSet<BigInteger> rcvTokenIds = new HashSet<>(token.processLogsAndStoreTransferEvents(receiveLogs, transferEvent, txHashes, realm));

        if (sendLogs.getLogs().size() > eventCount) eventCount = sendLogs.getLogs().size();

        HashSet<BigInteger> sendTokenIds = token.processLogsAndStoreTransferEvents(sendLogs, transferEvent, txHashes, realm);

        //register Transaction fetches
        for (String txHash : txHashes)
        {
            TransactionsService.addTransactionHashFetch(txHash, token.tokenInfo.chainId, token.getWallet());
        }

        return new Pair<>(eventCount, new Pair<>(rcvTokenIds, sendTokenIds));
    }

    private Pair<EthLog, EthLog> getTxLogs(Web3j web3j, EthFilter receiveFilter, EthFilter sendFilter) throws LogOverflowException, IOException
    {
        if (EthereumNetworkBase.getBatchProcessingLimit(token.tokenInfo.chainId) > 0 && !batchProcessingError.contains(token.tokenInfo.chainId))
        {
            return getBatchTxLogs(web3j, receiveFilter, sendFilter);
        }
        else
        {
            EthLog receiveLogs = web3j.ethGetLogs(receiveFilter).send();

            if (receiveLogs.hasError())
            {
                throw new LogOverflowException(receiveLogs.getError());
            }

            EthLog sentLogs = web3j.ethGetLogs(sendFilter).send();

            if (sentLogs.hasError())
            {
                throw new LogOverflowException(sentLogs.getError());
            }

            return new Pair<>(receiveLogs, sentLogs);
        }
    }

    private Pair<EthLog, EthLog> getBatchTxLogs(Web3j web3j, EthFilter receiveFilter, EthFilter sendFilter) throws LogOverflowException, IOException
    {
        BatchResponse rsp;

        try
        {
            rsp = web3j.newBatch()
                    .add(web3j.ethGetLogs(receiveFilter))
                    .add(web3j.ethGetLogs(sendFilter))
                    .send();
        }
        catch (ClassCastException e)
        {
            rsp = null;
        }

        if (rsp == null || rsp.getResponses().size() != 2)
        {
            batchProcessingError.add(token.tokenInfo.chainId);
            return getTxLogs(web3j, receiveFilter, sendFilter);
        }

        EthLog receiveLogs = (EthLog) rsp.getResponses().get(0);
        EthLog sendLogs = (EthLog) rsp.getResponses().get(1);

        if (receiveLogs.hasError())
        {
            throw new LogOverflowException(receiveLogs.getError());
        }
        else if (sendLogs.hasError())
        {
            throw new LogOverflowException(sendLogs.getError());
        }

        return new Pair<>(receiveLogs, sendLogs);
    }

    public String getActivityName(String toAddress)
    {
        String activityName = "";
        if (toAddress.equalsIgnoreCase(token.getWallet()))
        {
            // activity = RECEIVE
            activityName = "received";
        }
        else
        {
            // activity = SEND
            activityName = "sent";
        }
        return activityName;
    }

    public String getIds(Pair<List<BigInteger>, List<BigInteger>> ids)
    {
        StringBuilder sbFirst = new StringBuilder();
        StringBuilder sbSecond = new StringBuilder();
        boolean firstVal = true;
        //check lengths are the same
        if (ids.first.size() != ids.second.size()) return "";
        for (int i = 0; i < ids.first.size(); i++)
        {
            if (!firstVal)
            {
                sbFirst.append("-");
                sbSecond.append("-");
            }

            sbFirst.append(ids.first.get(i).toString());
            sbSecond.append(ids.second.get(i).toString());
            firstVal = false;
        }

        return (ids.first.size() == 1 && ids.second.get(0).equals(BigInteger.ONE)) ? sbFirst.toString() :
                sbFirst + ",value,uint256," + sbSecond;
    }

    public Pair<List<BigInteger>, List<BigInteger>> getEventIdResult(Type<?> ids, Type<?> values)
    {
        //int size = eventValues.getNonIndexedValues().size();
        List<BigInteger> _ids = new ArrayList<>();
        List<BigInteger> _count = new ArrayList<>();
        if (values == null)
        {
            _ids.add(((Uint256) ids).getValue());
            _count.add(BigInteger.ONE);
        }
        else
        {
            if (ids instanceof Uint256) //single
            {
               _ids.add((BigInteger) ids.getValue());
                _count.add((BigInteger) values.getValue());
            }
            else //array type
            {
                for (Object val : (ArrayList<?>)ids.getValue())
                {
                    if (val instanceof Uint256)
                    {
                        _ids.add(((Uint256) val).getValue());
                    }
                }
                for (Object val : (ArrayList<?>)values.getValue())
                {
                    if (val instanceof Uint256)
                    {
                        _count.add(((Uint256) val).getValue());
                    }
                }
            }
        }

        return new Pair<>(_ids, _count);
    }

    private String generateValueListForTransferEvent(String to, String from, String tokenID)
    {
        String TO_TOKEN = "[TO_ADDRESS]";
        String FROM_TOKEN = "[FROM_ADDRESS]";
        String AMOUNT_TOKEN = "[AMOUNT_TOKEN]";
        String VALUES = "from,address," + FROM_TOKEN + ",to,address," + TO_TOKEN + ",amount,uint256," + AMOUNT_TOKEN;

        return VALUES.replace(TO_TOKEN, to).replace(FROM_TOKEN, from).replace(AMOUNT_TOKEN, tokenID);
    }

    public void storeTransferData(Realm realm, String from, String to,
                                  Pair<List<BigInteger>, List<BigInteger>> idResult,
                                  String txHash)
    {
        String activityName = getActivityName(to);
        String value = getIds(idResult);
        String valueList = generateValueListForTransferEvent(to, from, value);
        realm.executeTransaction(r -> {
            storeTransferData(realm, txHash, valueList, activityName);
        });
    }

    private void storeTransferData(Realm instance, String hash, String valueList, String activityName)
    {
        if (activityName.equals("receive"))
        {
            instance.where(RealmTransfer.class)
                    .like("hash", RealmTransfer.databaseKey(token.tokenInfo.chainId, hash))
                    .findAll().deleteAllFromRealm();
        }

        RealmTransfer matchingEntry = instance.where(RealmTransfer.class)
                .equalTo("hash", RealmTransfer.databaseKey(token.tokenInfo.chainId, hash))
                .equalTo("tokenAddress", token.tokenInfo.address)
                .equalTo("eventName", activityName)
                .equalTo("transferDetail", valueList)
                .findFirst();

        if (matchingEntry == null) //prevent duplicates
        {
            matchingEntry = instance.createObject(RealmTransfer.class);
            matchingEntry.setHashKey(token.tokenInfo.chainId, hash);
            matchingEntry.setTokenAddress(token.tokenInfo.address);
        }

        matchingEntry.setEventName(activityName);
        matchingEntry.setTransferDetail(valueList);
        instance.insertOrUpdate(matchingEntry);
    }

    private void EVENT_DEBUG(String message)
    {
        if (EVENT_SYNC_DEBUGGING)
        {
            Timber.tag(TAG).i(token.tokenInfo.chainId + " " + token.tokenInfo.address + ": " + message);
        }
    }
}
