package com.techhurts.calculator;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CalculatorActivity extends Activity {

    private TextView mTvExpression;
    private TextView mTvResult;

    private String mCurrentInput = "0";
    private double mStoredValue = 0;
    private char mPendingOp = 0;
    private boolean mFreshInput = true;
    private String mExprLine = "";
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        mAppWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        mTvExpression = findViewById(R.id.tv_expression);
        mTvResult = findViewById(R.id.tv_result);

        int[] digitIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                          R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};
        for (int i = 0; i < digitIds.length; i++) {
            final String d = String.valueOf(i);
            ((Button) findViewById(digitIds[i])).setOnClickListener(v -> digit(d));
        }

        findViewById(R.id.btn_decimal).setOnClickListener(v -> decimal());
        findViewById(R.id.btn_toggle_sign).setOnClickListener(v -> toggleSign());
        findViewById(R.id.btn_back).setOnClickListener(v -> backspace());
        findViewById(R.id.btn_add).setOnClickListener(v -> operator('+'));
        findViewById(R.id.btn_sub).setOnClickListener(v -> operator('-'));
        findViewById(R.id.btn_mul).setOnClickListener(v -> operator('×'));
        findViewById(R.id.btn_div).setOnClickListener(v -> operator('÷'));
        findViewById(R.id.btn_equals).setOnClickListener(v -> equals());
        findViewById(R.id.btn_c).setOnClickListener(v -> clearCalc());
        findViewById(R.id.btn_ce).setOnClickListener(v -> clearAll());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyResult());

        updateDisplay();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) makeButtonsSquare();
    }

    private void makeButtonsSquare() {
        View buttonArea = findViewById(R.id.button_area);
        int buttonSize = buttonArea.getWidth() / 4;
        if (buttonSize <= 0) return;
        int[] rowIds = {R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4};
        for (int rowId : rowIds) {
            View row = findViewById(rowId);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getLayoutParams();
            p.height = buttonSize;
            p.weight = 0;
            row.setLayoutParams(p);
        }
    }

    private void digit(String d) {
        if (mFreshInput) {
            mCurrentInput = d.equals("0") ? "0" : d;
            mFreshInput = false;
        } else {
            mCurrentInput = mCurrentInput.equals("0") ? d : mCurrentInput + d;
        }
        updateDisplay();
    }

    private void decimal() {
        if (mFreshInput) { mCurrentInput = "0."; mFreshInput = false; }
        else if (!mCurrentInput.contains(".")) mCurrentInput += ".";
        updateDisplay();
    }

    private void toggleSign() {
        if (mCurrentInput.equals("0")) return;
        mCurrentInput = mCurrentInput.startsWith("-")
                ? mCurrentInput.substring(1) : "-" + mCurrentInput;
        updateDisplay();
    }

    private void backspace() {
        if (mFreshInput) return;
        boolean justSign = mCurrentInput.startsWith("-") && mCurrentInput.length() == 2;
        if (mCurrentInput.length() <= 1 || justSign) {
            mCurrentInput = "0"; mFreshInput = true;
        } else {
            mCurrentInput = mCurrentInput.substring(0, mCurrentInput.length() - 1);
        }
        updateDisplay();
    }

    private void operator(char op) {
        double current = parse(mCurrentInput);
        if (mPendingOp != 0 && !mFreshInput) {
            double res = compute(mStoredValue, mPendingOp, current);
            mStoredValue = res;
            mCurrentInput = fmt(res);
        } else {
            mStoredValue = current;
        }
        mPendingOp = op;
        mExprLine = fmt(mStoredValue) + " " + op + " ";
        mFreshInput = true;
        updateDisplay();
    }

    private void equals() {
        if (mPendingOp == 0) return;
        double current = parse(mCurrentInput);
        char op = mPendingOp;
        double result = compute(mStoredValue, op, current);
        String resultStr = fmt(result);
        mExprLine = fmt(mStoredValue) + " " + op + " " + fmt(current) + " =";
        mCurrentInput = resultStr;
        mPendingOp = 0;
        mFreshInput = true;
        if (mAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            CalculatorWidgetProvider.saveAndUpdate(
                    this, mAppWidgetId, fmt(mStoredValue) + op + fmt(current), resultStr);
        }
        updateDisplay();
    }

    private void clearCalc() {
        mCurrentInput = "0"; mStoredValue = 0; mPendingOp = 0;
        mFreshInput = true; mExprLine = "";
        updateDisplay();
    }

    private void clearAll() {
        clearCalc();
        if (mAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
            CalculatorWidgetProvider.clearAndUpdate(this, mAppWidgetId);
    }

    private void copyResult() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Calculator", mCurrentInput));
        Toast.makeText(this, "Copied: " + mCurrentInput, Toast.LENGTH_SHORT).show();
    }

    private void updateDisplay() {
        mTvExpression.setText(mExprLine);
        mTvResult.setText(mCurrentInput);
    }

    private double parse(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private double compute(double a, char op, double b) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '×': return a * b;
            case '÷': return b == 0 ? Double.NaN : a / b;
        }
        return b;
    }

    private String fmt(double v) {
        if (Double.isNaN(v)) return "Err";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "-∞";
        if (v == Math.floor(v) && Math.abs(v) < 1e12) return String.valueOf((long) v);
        return String.format("%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
