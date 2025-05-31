package de.thedevstack.piratx.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import de.monocles.chat.RegisterMonoclesActivity;
import eu.siacs.conversations.ui.MagicCreateActivity;
import eu.siacs.conversations.ui.WelcomeActivity;

public class PiratXWelcomeActivity extends WelcomeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding.registerNewAccount.setOnClickListener(v -> {
            final Intent intent = new Intent(this, MagicCreateActivity.class);
            addInviteUri(intent);
            startActivity(intent);
        });
    }
}