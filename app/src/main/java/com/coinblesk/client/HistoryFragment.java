/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.client;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinblesk.client.ui.widgets.RecyclerView;
import com.coinblesk.client.config.Constants;
import com.coinblesk.payments.WalletService;
import com.coinblesk.client.models.TransactionWrapper;

import java.util.ArrayList;

/**
 * Created by ckiller
 */

public class HistoryFragment extends android.support.v4.app.Fragment {

    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
        recyclerView = (RecyclerView) view.findViewById(R.id.txhistoryview);
        View empty = view.findViewById(R.id.txhistory_emptyview);
        recyclerView.setEmptyView(empty);
        recyclerView.setAdapter(new TransactionWrapperRecyclerViewAdapter(new ArrayList<TransactionWrapper>()));
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));

        return view;
    }

    /* ------------------- PAYMENTS INTEGRATION STARTS HERE  ------------------- */
    private final BroadcastReceiver walletBalanceChangeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            recyclerView.setAdapter(new TransactionWrapperRecyclerViewAdapter(walletServiceBinder.getTransactionsByTime()));
        }
    };

    private WalletService.WalletServiceBinder walletServiceBinder;

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this.getActivity(), WalletService.class);
        this.getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.getActivity().unbindService(serviceConnection);
        LocalBroadcastManager.getInstance(HistoryFragment.this.getActivity()).unregisterReceiver(walletBalanceChangeBroadcastReceiver);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            walletServiceBinder = (WalletService.WalletServiceBinder) binder;
            IntentFilter filter = new IntentFilter(Constants.WALLET_TRANSACTIONS_CHANGED_ACTION);
            filter.addAction(Constants.WALLET_READY_ACTION);
            filter.addAction(Constants.EXCHANGE_RATE_CHANGED_ACTION);

            LocalBroadcastManager.getInstance(HistoryFragment.this.getActivity()).registerReceiver(walletBalanceChangeBroadcastReceiver, filter);
            recyclerView.setAdapter(new TransactionWrapperRecyclerViewAdapter(walletServiceBinder.getTransactionsByTime()));
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            walletServiceBinder = null;
        }
    };
    /* -------------------- PAYMENTS INTEGRATION ENDS HERE  -------------------- */
}
