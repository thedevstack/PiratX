package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNumberBinding;
import eu.siacs.conversations.ui.drawable.TextDrawable;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class EnterPhoneNumberActivity extends AppCompatActivity {

    private static final int REQUEST_CHOOSE_COUNTRY = 0x1234;

    private ActivityEnterNumberBinding binding;
    private String region = null;
    private final TextWatcher countryCodeTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            final String text = editable.toString();
            try {
                final int oldCode = region != null ? PhoneNumberUtilWrapper.getInstance(EnterPhoneNumberActivity.this).getCountryCodeForRegion(region) : 0;
                final int code = Integer.parseInt(text);
                if (oldCode != code) {
                    region = PhoneNumberUtilWrapper.getInstance(EnterPhoneNumberActivity.this).getRegionCodeForCountryCode(code);
                }
                if ("ZZ".equals(region)) {
                    binding.country.setText(TextUtils.isEmpty(text) ? R.string.choose_a_country : R.string.invalid_country_code);
                } else {
                    binding.number.requestFocus();
                    binding.country.setText(PhoneNumberUtilWrapper.getCountryForCode(region));
                }
            } catch (NumberFormatException e) {
                binding.country.setText(TextUtils.isEmpty(text) ? R.string.choose_a_country : R.string.invalid_country_code);
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String region = savedInstanceState != null ? savedInstanceState.getString("region") : null;
        if (region != null) {
            this.region = region;
        } else {
            this.region = PhoneNumberUtilWrapper.getUserCountry(this);
        }

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_number);
        this.binding.countryCode.setCompoundDrawables(new TextDrawable(this.binding.countryCode, "+"), null, null, null);
        this.binding.country.setOnClickListener(this::onSelectCountryClick);
        this.binding.next.setOnClickListener(this::onNextClick);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.countryCode.addTextChangedListener(this.countryCodeTextWatcher);
        this.binding.countryCode.setText(String.valueOf(PhoneNumberUtilWrapper.getInstance(this).getCountryCodeForRegion(this.region)));
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (this.region != null) {
            savedInstanceState.putString("region", this.region);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    private void onNextClick(View v) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        try {
            final Editable number = this.binding.number.getText();
            final String input = number.toString();
            final Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtilWrapper.getInstance(this).parse(input, region);
            this.binding.countryCode.setText(String.valueOf(phoneNumber.getCountryCode()));
            number.clear();
            number.append(String.valueOf(phoneNumber.getNationalNumber()));
            final String formattedPhoneNumber = PhoneNumberUtilWrapper.getInstance(this).format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);

            if (PhoneNumberUtilWrapper.getInstance(this).isValidNumber(phoneNumber)) {
                builder.setMessage(Html.fromHtml(getString(R.string.we_will_be_verifying, formattedPhoneNumber)));
                builder.setNegativeButton(R.string.edit, null);
                builder.setPositiveButton(R.string.ok, (dialog, which) -> onPhoneNumberEntered(phoneNumber));
            } else {
                builder.setMessage(getString(R.string.not_a_valid_phone_number, formattedPhoneNumber));
                builder.setPositiveButton(R.string.ok, null);
            }
            Log.d(Config.LOGTAG, phoneNumber.toString());
        } catch (NumberParseException e) {
            builder.setMessage(R.string.please_enter_your_phone_number);
            builder.setPositiveButton(R.string.ok, null);
        }
        builder.create().show();
    }

    private void onSelectCountryClick(View view) {
        Intent intent = new Intent(this, ChooseCountryActivity.class);
        startActivityForResult(intent, REQUEST_CHOOSE_COUNTRY);
    }

    private void onPhoneNumberEntered(Phonenumber.PhoneNumber phoneNumber) {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CHOOSE_COUNTRY) {
            String region = data.getStringExtra("region");
            if (region != null) {
                this.region = region;
                final int countryCode = PhoneNumberUtilWrapper.getInstance(this).getCountryCodeForRegion(region);
                this.binding.countryCode.setText(String.valueOf(countryCode));
            }
        }
    }

}
