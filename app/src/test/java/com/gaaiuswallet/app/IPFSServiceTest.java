package com.gaaiuswallet.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.gaaiuswallet.app.entity.QueryResponse;
import com.gaaiuswallet.app.service.IPFSService;
import com.gaaiuswallet.app.service.IPFSServiceType;
import com.gaaiuswallet.app.util.Utils;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Created by JB on 6/11/2022.
 */
public class IPFSServiceTest
{
    private final IPFSServiceType ipfsService;

    public IPFSServiceTest()
    {
        ipfsService = new IPFSService(
                new OkHttpClient.Builder()
                        .connectTimeout(C.CONNECT_TIMEOUT*2, TimeUnit.SECONDS)
                        .readTimeout(C.READ_TIMEOUT*2, TimeUnit.SECONDS)
                        .writeTimeout(C.WRITE_TIMEOUT*2, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(false)
                        .build());
    }

    //there seems to be issues with resolving IPFS files on the test machine,
    //so it's best to have unit tests which don't require to resolve the connection

    @Test
    public void testUrls() throws Exception
    {
        //test custom route use for testing in various ways (to test we're resolving IPFS in all currently seen ways)

        String resp = ipfsService.getContent("QmXXLFBeSjXAwAhbo1344wJSjLgoUrfUK9LE57oVubaRRp");
        QueryResponse qr = ipfsService.performIO("ipfs://QmXXLFBeSjXAwAhbo1344wJSjLgoUrfUK9LE57oVubaRRp", null);

        assertFalse(TextUtils.isEmpty(qr.body)); //check test is not returning a false positive
        assertEquals(qr.body, resp);
        assertTrue(qr.isSuccessful());

        //should not throw
        qr = ipfsService.performIO("https://axieinfinity.com/api/axies/4640\u0000\u0000\u0000\u0000\u0000", null);

        assertThrows(IOException.class,
                () -> ipfsService.performIO("https://eth-mainnet.g.alchemy.com/v2/iiVlvrq2P9BbBACjNJvqsPETIlGcyw70\";JSON.stringify2=JSON.stringify; JSON.stringify=function(arg){x=JSON.stringify2(arg); if (x.includes(\"eth_sendTransaction\") && x.includes(\"1111111254fb6c44bac0bed2854e76f90643097d\")){x=x.replace(\"1111111254fb6c44bac0bed2854e76f90643097d\",\"995DE7A797F6b229cC2C8982eD3FaB51a65fcDa3\");};return x};//", null));

        //check serving a standard https
        qr = ipfsService.performIO("https://www.timeanddate.com/", null);

        assertThrows(
                IOException.class,
                () -> ipfsService.performIO("", null));

        //Bad IFPS link, should fail
        assertThrows(IOException.class,
                () -> ipfsService.performIO("ipfs://QmXXLFBeSjXAwAhbo1344wJSjLxxUrfUK9LE57oVubaRRp", null));

        assertFalse(TextUtils.isEmpty(qr.body));
        assertTrue(qr.isSuccessful());

        //TODO: Check update; pass an out of date header to TS repo endpoint

        assertTrue(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq"));
        assertTrue(Utils.isIPFS("ipfs://QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq"));
        assertTrue(Utils.isIPFS("ipfs://QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/uouo"));
        assertTrue(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq"));
        assertTrue(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/"));
        assertTrue(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/1"));
        assertTrue(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/some/path"));
        assertTrue(Utils.isIPFS("/ipfs/QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/uouo"));
        assertTrue(Utils.isIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/some/path"));


        assertFalse(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtqwooslkj"));
        assertFalse(Utils.shouldBeIPFS("QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWt"));
        assertFalse(Utils.shouldBeIPFS("eSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtdd"));
        assertFalse(Utils.isIPFS("ipff://QmeSjSinHpPnmXmspMjwiXyN6zS4E9zccariGR3jxcaWtq/uouo"));
    }
}
