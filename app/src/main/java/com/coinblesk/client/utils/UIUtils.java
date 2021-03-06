/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.coinblesk.client.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.widget.ImageView;

import com.coinblesk.client.AppConstants;
import com.coinblesk.client.R;
import com.coinblesk.client.models.TransactionWrapper;
import com.coinblesk.util.BitcoinUtils;
import com.google.gson.Gson;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.BtcFixedFormat;
import org.bitcoinj.utils.BtcFormat;
import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by ckiller on 03/03/16.
 */

public class UIUtils {


    private static final String TAG = UIUtils.class.getName();

    public static SpannableString getLargeBalance(Context context, Coin balanceCoin, Fiat balanceFiat) {
        // Get all Preferences

        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        String isLargeAmount = SharedPrefUtils.getPrimaryBalance(context);

        // TODO -> As of now, currency retrieved via getBalanceFiat().getCurrencyCode()
        // TODO -> Does this make sense? What it a user changes his primary currency?
//        String fiatCurrency = prefs.getString(FIAT_CURRENCY_PREF_KEY, null);

        SpannableString result = new SpannableString("");

        switch (isLargeAmount) {
            case AppConstants.BTC_AS_PRIMARY:
                result = toLargeSpannable(context, scaleCoin(balanceCoin, coinDenomination), coinDenomination);
                break;
            case AppConstants.FIAT_AS_PRIMARY:
                result = toLargeSpannable(context, balanceFiat.toPlainString(), balanceFiat.getCurrencyCode());
                break;
        }

        return result;
    }

    public static SpannableString getSmallBalance(Context context, Coin balanceCoin, Fiat balanceFiat) {
        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        String isLargeAmount = SharedPrefUtils.getPrimaryBalance(context);
        SpannableString result = new SpannableString("");

        switch (isLargeAmount) {
            case AppConstants.BTC_AS_PRIMARY:
                result = toSmallSpannable(balanceFiat.toPlainString(), balanceFiat.getCurrencyCode());
                break;
            case AppConstants.FIAT_AS_PRIMARY:
                result = toSmallSpannable(scaleCoin(balanceCoin, coinDenomination), coinDenomination);
                break;
        }
        return result;

    }

    public static String scaleCoin(Coin coin, String coinDenomination) {
        String result = "";
        // Dont try to use the Builder,"You cannot invoke both scale() and style()"... Add Symbol (Style) Manually
        switch (coinDenomination) {
            case AppConstants.COIN:
                result = BtcFormat.getInstance(BtcFormat.COIN_SCALE).format(coin);
                break;
            case AppConstants.MILLICOIN:
                result = BtcFormat.getInstance(BtcFormat.MILLICOIN_SCALE).format(coin);
                break;
            case AppConstants.MICROCOIN:
                result = BtcFormat.getInstance(BtcFormat.MICROCOIN_SCALE).format(coin);
                break;
        }

        return result;
    }

    public static SpannableString scaleCoinForDialogs(Coin coin, Context context) {
        String result = "";
        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        // Dont try to use the Builder,"You cannot invoke both scale() and style()"... Add Symbol (Style) Manually
        switch (coinDenomination) {
            case AppConstants.COIN:
                result = BtcFormat.getInstance(BtcFormat.COIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
                break;
            case AppConstants.MILLICOIN:
                result = BtcFormat.getInstance(BtcFormat.MILLICOIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
                break;
            case AppConstants.MICROCOIN:
                result = BtcFormat.getInstance(BtcFormat.MICROCOIN_SCALE).format(coin, 0, BtcFixedFormat.REPEATING_PLACES);
                break;
        }

        // 1.3F Size Span necessary - otherwise Overflowing Edge of Dialog
        float sizeSpan = 1.3F;

        return toLargeSpannable(context, result, coinDenomination, sizeSpan);
    }


    public static Coin getValue(String amount, Context context) {
        BigDecimal bdAmount = new BigDecimal(amount);

        BigDecimal multiplicand = new BigDecimal(Coin.COIN.getValue());
        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        switch (coinDenomination) {
            case AppConstants.MILLICOIN:
                multiplicand = new BigDecimal((Coin.MILLICOIN.getValue()));
                break;
            case AppConstants.MICROCOIN:
                multiplicand = new BigDecimal((Coin.MICROCOIN.getValue()));
                break;
        }

        return Coin.valueOf((bdAmount.multiply(multiplicand).longValue()));

    }

    public static String coinToAmount(Coin coin, Context context) {
        // transform a given coin value to the "amount string".
        BigDecimal coinAmount = new BigDecimal(coin.getValue());
        BigDecimal div;
        String coinDenomination = SharedPrefUtils.getBitcoinScalePrefix(context);
        switch (coinDenomination) {
            case AppConstants.MILLICOIN:
                div = new BigDecimal(Coin.MILLICOIN.getValue());
                break;
            case AppConstants.MICROCOIN:
                div = new BigDecimal(Coin.MICROCOIN.getValue());
                break;
            default:
                div = new BigDecimal(Coin.COIN.getValue());
        }
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.DOWN);
        df.setMaximumFractionDigits(4);
        DecimalFormatSymbols decFormat = new DecimalFormatSymbols();
        decFormat.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(decFormat);
        String amount = df.format(coinAmount.divide(div));
        return amount;
    }

    public static SpannableString toSmallSpannable(String amount, String currency) {
        StringBuffer stringBuffer = new StringBuffer(amount + " " + currency);
        SpannableString spannableString = new SpannableString(stringBuffer);
        return spannableString;
    }

    public static SpannableString toLargeSpannable(Context context, String amount, String currency) {
        final int amountLength = amount.length();
        SpannableString result = new SpannableString(new StringBuffer(amount + " " + currency.toString()));
        result.setSpan(new RelativeSizeSpan(2), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(Color.WHITE), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)), amountLength, result.length(), 0);
        return result;
    }

    public static SpannableString toLargeSpannable(Context context, String amount, String currency, float sizeSpan) {
        final int amountLength = amount.length();
        SpannableString result = new SpannableString(new StringBuffer(amount + " " + currency.toString()));
        result.setSpan(new RelativeSizeSpan(sizeSpan), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(Color.WHITE), 0, amountLength, 0);
        result.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)), amountLength, result.length(), 0);
        return result;
    }

    public static int getLargeTextSize(Context context, int amountLength) {

            /*final int screenLayout = context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        switch (screenLayout) {
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                // TABLETS
                break;
            default:
                // PHONES
                break;
        }*/


        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 6)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 7)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 6)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 7)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }

    public static int getLargeTextSizeForBalance(Context context, int amountLength) {

        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 10)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 12)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 12)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 13)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 14)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }

    public static int getLargeTextSizeForDialogs(Context context, int amountLength) {

        int textSize = context.getResources().getInteger(R.integer.text_size_xxlarge);
        final int orientation = context.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                textSize = context.getResources().getInteger(R.integer.text_size_large_landscape);
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium_landscape);
                if (amountLength > 9)
                    textSize = context.getResources().getInteger(R.integer.text_size_small_landscape);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                if (amountLength > 8)
                    textSize = context.getResources().getInteger(R.integer.text_size_xlarge);
                if (amountLength > 9)
                    textSize = context.getResources().getInteger(R.integer.text_size_large);
                if (amountLength > 10)
                    textSize = context.getResources().getInteger(R.integer.text_size_medium);
                break;
        }

        return textSize;
    }


    public static SpannableString toFriendlyAmountString(Context context, TransactionWrapper transaction) {
        StringBuffer friendlyAmount = new StringBuffer(transaction.getAmount().toFriendlyString());
        final int coinLength = friendlyAmount.length() - 3;

        friendlyAmount.append(" ~ " + transaction.getTransaction().getExchangeRate().coinToFiat(transaction.getAmount()).toFriendlyString());
        friendlyAmount.append(System.getProperty("line.separator") + "(1 BTC = " + transaction.getTransaction().getExchangeRate().fiat.toFriendlyString() + " as of now)");
        final int amountLength = friendlyAmount.length();

        SpannableString friendlySpannable = new SpannableString(friendlyAmount);
        friendlySpannable.setSpan(new RelativeSizeSpan(2), 0, coinLength, 0);
        friendlySpannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent)), coinLength, (coinLength + 4), 0);
        friendlySpannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.main_color_400)), (coinLength + 4), amountLength, 0);
        return friendlySpannable;

    }

    public static String formatCustomButton(String description, String amount) {
        String result = amount + System.getProperty("line.separator") + description;
        return result;
    }

    public static SpannableString toFriendlySnackbarString(Context context, String input) {
        final ForegroundColorSpan whiteSpan = new ForegroundColorSpan(ContextCompat.getColor(context, R.color.colorAccent));
        final SpannableString snackbarText = new SpannableString(input);
        snackbarText.setSpan(whiteSpan, 0, snackbarText.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return snackbarText;
    }


    public static String getSum(String amounts) {
        String delims = "[+]";
        String result = "0";
        String[] tokens = amounts.split(delims);
        if (tokens.length > 1) {
            BigDecimal sum = new BigDecimal(0);
            for (int i = 0; i < tokens.length; i++) {
                sum = sum.add(new BigDecimal(tokens[i]));
            }
            result = sum.toString();
        }
        return result;
    }

    public static boolean stringIsNotZero(String amountString){
        //Checks if a string is actually of Zero value 0.00 0 0.000 etc.
        BigDecimal bd = new BigDecimal(amountString);
        if(bd.compareTo(BigDecimal.ZERO) == 0){
            return false;
        }
        return true;
    }

    public static List<String> getCustomButton(Context context, String customKey) {
        if (!SharedPrefUtils.isCustomButtonEmpty(context, customKey)) {
            String json = SharedPrefUtils.getCustomButton(context, customKey);
            Gson gson = new Gson();
            String[] contentArray = gson.fromJson(json, String[].class);
            List<String> contentList;
            try {
                contentList = Arrays.asList(contentArray);
                return contentList;
            } catch (Exception e) {
                Log.e(TAG, "Could not decode content from json to a list.");
            }
        }

        return null;
    }


    public static void formatConnectionIcon(Context context, ImageView imageView, String status) {
        Set<String> connectionSettings = SharedPrefUtils.getConnectionSettings(context);
        // see: styles.xml -> card_view_connection_icon
        float alpphaDeactivated = 0.25f;
        imageView.setAlpha(alpphaDeactivated);
        imageView.clearColorFilter();

         // Set the Icon Color and Visibility
        if (connectionSettings != null) {
            for (String s : connectionSettings) {
                switch (s) {
                    case AppConstants.NFC_ACTIVATED:
                        if (status.equals(AppConstants.NFC_ACTIVATED)) {
                            makeVisible(context, imageView);
                        }
                        break;
                    case AppConstants.BT_ACTIVATED:
                        if (status.equals(AppConstants.BT_ACTIVATED)) {
                            makeVisible(context, imageView);
                        }
                        break;
                    case AppConstants.WIFIDIRECT_ACTIVATED:
                        if (status.equals(AppConstants.WIFIDIRECT_ACTIVATED)) {
                            makeVisible(context, imageView);
                        }
                        break;
                }
            }
        }
    }

    private static void makeVisible(Context context, ImageView imageView) {
        imageView.setAlpha(AppConstants.ICON_VISIBLE);
        imageView.setColorFilter(ContextCompat.getColor(context, R.color.colorAccent));
    }

    public static int getFractionalLengthFromString(String amount) {
        // Escape '.' otherwise won't work
        String delims = "\\.";
        int length = -1;
        String[] tokens = amount.split(delims);
        if (tokens.length == 2)
            length = tokens[1].length();
        return length;
    }

    public static int getIntegerLengthFromString(String amount) {
        // Escape '.' otherwise won't work
        String delims = "\\.";
        int length = -1;
        String[] tokens = amount.split(delims);
        if (tokens.length == 1)
            length = tokens[0].length();
        return length;
    }

    public static boolean isDecimal(String amount) {
        return ((amount.contains(".")) ? true : false);

    }

    public static int getDecimalThreshold(String coinDenomination) {
        int threshold = 2;
        switch (coinDenomination) {
            case AppConstants.COIN:
                threshold = 4;
                break;
            case AppConstants.MILLICOIN:
                threshold = 5;
                break;
            case AppConstants.MICROCOIN:
                threshold = 2;
                break;
        }
        return threshold;
    }

    public static int getStatusColorFilter(int depthInBlock, boolean instantPayment) {
        if (instantPayment)
            return Color.parseColor(AppConstants.COLOR_COLOR_ACCENT);
        if (depthInBlock == 0)
            return Color.parseColor(AppConstants.COLOR_MATERIAL_LIGHT_YELLOW_900);
        return Color.parseColor(AppConstants.COLOR_WHITE);
    }

    public static String lockedUntilText(long lockTime) {
        String lockedUntil;
        if (BitcoinUtils.isLockTimeByTime(lockTime)) {
            lockedUntil = DateFormat.getDateTimeInstance().format(new Date(lockTime * 1000L));
        } else {
            lockedUntil = String.format("block %d", lockTime);
        }
        return lockedUntil;
    }
}
