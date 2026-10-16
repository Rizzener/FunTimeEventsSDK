package net.funtimeevents.net;

import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic reproduction of the old fixture race, with a negative control. */
public final class SendFutureAccountingCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{verify();}
    static void verify()throws Exception {
        var root=new CompletableFuture<String>();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pending=new AtomicInteger(1);
        var observed=root.whenComplete((v,e)->{
            entered.countDown();
            try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("fixture barrier timeout");}
            catch(InterruptedException failure){Thread.currentThread().interrupt();throw new CompletionException(failure);}
            pending.decrementAndGet();
        });
        var completer=new Thread(()->root.complete("sent"),"Fixture-completer");completer.start();
        try {
            require(entered.await(10,TimeUnit.SECONDS),"completion callback not entered");
            require(root.get(1,TimeUnit.SECONDS).equals("sent"),"root not completed");
            require(pending.get()==1&&!observed.isDone(),"failed to reproduce root-before-callback ordering");
        }finally{release.countDown();completer.join(10_000);}
        require(!completer.isAlive()&&observed.isDone()&&pending.get()==0,"accounting did not complete");

        // Deliberately overlap two unfinished sends: the corrected fake must
        // still detect a broken writer. No lock/serialization is added to it.
        var driver=new FakeSocketClient();var socket=new FakeSocketClient.Socket(new WebSocket.Listener(){},driver);
        var first=socket.sendText("one",true);var firstRoot=socket.held;
        var second=socket.sendText("two",true);var secondRoot=socket.held;
        require(!first.isDone()&&!second.isDone()&&socket.maxPending.get()==2,"negative overlap control not detected");
        firstRoot.complete(socket);first.get(1,TimeUnit.SECONDS);
        require(socket.active.get()==1,"first completed result published before accounting");
        secondRoot.complete(socket);second.get(1,TimeUnit.SECONDS);
        require(socket.active.get()==0,"second completed result published before accounting");
        System.out.println("[FIXTURE] FUTURE_ACCOUNTING_PASS root_done_before_callback=true negative_overlap_detected=2 java="+Runtime.version());
    }
}
