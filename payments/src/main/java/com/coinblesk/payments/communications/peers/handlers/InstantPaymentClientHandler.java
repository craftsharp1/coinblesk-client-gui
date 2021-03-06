package com.coinblesk.payments.communications.peers.handlers;

import android.util.Log;

import com.coinblesk.payments.WalletService;
import com.coinblesk.der.DERObject;
import com.coinblesk.payments.communications.peers.PaymentRequestDelegate;
import com.coinblesk.payments.communications.steps.PaymentFinalSignatureOutpointsSendStep;
import com.coinblesk.payments.communications.steps.PaymentRefundSendStep;
import com.coinblesk.payments.communications.steps.PaymentRequestReceiveStep;
import com.coinblesk.client.models.RefundTransactionWrapper;

import org.bitcoinj.core.Transaction;

import java.io.InputStream;
import java.io.OutputStream;

import ch.papers.objectstorage.UuidObjectStorage;
import ch.papers.objectstorage.UuidObjectStorageException;
import ch.papers.objectstorage.listeners.OnResultListener;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 04/03/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class InstantPaymentClientHandler extends DERObjectStreamHandler {
    private final static String TAG = InstantPaymentClientHandler.class.getSimpleName();
    private final WalletService.WalletServiceBinder walletServiceBinder;
    private final PaymentRequestDelegate paymentRequestDelegate;

    public InstantPaymentClientHandler(InputStream inputStream, OutputStream outputStream, WalletService.WalletServiceBinder walletServiceBinder, PaymentRequestDelegate paymentRequestDelegate) {
        super(inputStream, outputStream);
        this.paymentRequestDelegate = paymentRequestDelegate;
        this.walletServiceBinder = walletServiceBinder;
    }

    @Override
    public void run() {
        Log.d(TAG, "kick off");
        try {
            writeDERObject(DERObject.NULLOBJECT); // we kick off the process
            PaymentRequestReceiveStep paymentRequestReceiveStep = new PaymentRequestReceiveStep(walletServiceBinder);
            final DERObject paymentRequestResponse = paymentRequestReceiveStep.process(readDERObject());
            Log.d(TAG, "got request, authorizing user");
            if (paymentRequestDelegate.isPaymentRequestAuthorized(paymentRequestReceiveStep.getBitcoinURI())) {
                writeDERObject(paymentRequestResponse);
                final PaymentRefundSendStep paymentRefundSendStep = new PaymentRefundSendStep(this.walletServiceBinder, paymentRequestReceiveStep.getBitcoinURI(), paymentRequestReceiveStep.getTimestamp());
                writeDERObject(paymentRefundSendStep.process(readDERObject()));
                final PaymentFinalSignatureOutpointsSendStep paymentFinalSignatureOutpointsSendStep = new PaymentFinalSignatureOutpointsSendStep(walletServiceBinder, paymentRequestReceiveStep.getBitcoinURI().getAddress(), paymentRefundSendStep.getClientSignatures(), paymentRefundSendStep.getServerSignatures(), paymentRefundSendStep.getFullSignedTransaction(), paymentRefundSendStep.getHalfSignedRefundTransaction());
                writeDERObject(paymentFinalSignatureOutpointsSendStep.process(readDERObject()));
                Log.d(TAG, "payment was successful, we're done!");
                // payment was successful in every way, commit that tx
                walletServiceBinder.commitAndBroadcastTransaction(paymentRefundSendStep.getFullSignedTransaction());
                final Transaction fullsignedRefundTransaction = paymentFinalSignatureOutpointsSendStep.getFullSignedRefundTransation();
                UuidObjectStorage.getInstance().addEntry(new RefundTransactionWrapper(fullsignedRefundTransaction), new OnResultListener<RefundTransactionWrapper>() {
                    @Override
                    public void onSuccess(RefundTransactionWrapper refundTransactionWrapper) {
                        try {
                            UuidObjectStorage.getInstance().commit();
                        } catch (UuidObjectStorageException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(String s) {
                        Log.d(TAG, s);
                    }
                }, RefundTransactionWrapper.class);

                paymentRequestDelegate.onPaymentSuccess();
            }
        } catch (Exception e) {
            Log.e(TAG, "Payment failed due to exception: ", e);
            paymentRequestDelegate.onPaymentError(e.getMessage());
        }
    }
}
