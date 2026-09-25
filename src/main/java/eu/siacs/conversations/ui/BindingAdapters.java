package eu.siacs.conversations.ui;

import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;

import de.monocles.chat.EmojiSearch;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.utils.UIHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.function.Function;

public class BindingAdapters {
    public static void setReactionsOnReceived(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked,
            final Consumer<EmojiSearch.CustomEmoji> onCustomReaction,
            final Consumer<Reaction> onCustomReactionRemove,
            final Runnable addReaction) {
        setReactions(chipGroup, reactions, true, onModifiedReactions, onDetailsClicked, onCustomReaction, onCustomReactionRemove, addReaction);
    }

    public static void setReactionsOnSent(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked) {
        setReactions(chipGroup, reactions, false, onModifiedReactions, onDetailsClicked, null, null, null);
    }

    private static void setReactions(
            final ChipGroup chipGroup,
            final Reaction.Aggregated aggregated,
            final boolean onReceived,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked,
            final Consumer<EmojiSearch.CustomEmoji> onCustomReaction,
            final Consumer<Reaction> onCustomReactionRemove,
            final Runnable addReaction) {
        final var context = chipGroup.getContext();
        final var size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, context.getResources().getDisplayMetrics());
        final var corner = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, context.getResources().getDisplayMetrics());
        final List<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>> reactions = aggregated.reactions;
        if (reactions == null || reactions.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
        } else {
            final boolean largeFont = new AppSettings(context).isLargeFont();
            // Resolved once for the whole group rather than per chip; each of these walks the theme.
            final var ourReactionColor =
                    MaterialColors.getColorStateListOrNull(
                            context, com.google.android.material.R.attr.colorSurfaceContainerHighest);
            final var theirReactionColor =
                    MaterialColors.getColorStateListOrNull(
                            context, com.google.android.material.R.attr.colorSurfaceContainerLow);
            chipGroup.setVisibility(View.VISIBLE);
            // A Chip is expensive to build, and this runs for every reacted message that scrolls
            // into view, so the group keeps the chips it already has and only the difference in
            // count is created or dropped.
            final int needed = reactions.size() + (addReaction == null ? 0 : 1);
            if (chipGroup.getChildCount() > needed) {
                chipGroup.removeViews(needed, chipGroup.getChildCount() - needed);
            }
            int index = 0;
            for (final var reaction : reactions) {
                final var emoji = reaction.getKey();
                final var count = reaction.getValue().size();
                final Chip chip = chipAt(chipGroup, index++, size, corner, largeFont);
                emoji.setupChip(chip, count);
                final var oneOfOurs = reaction.getValue().stream().filter(r -> !r.received).findFirst();
                // received = surface; sent = surface high matches bubbles
                chip.setChipBackgroundColor(
                        oneOfOurs.isPresent() ? ourReactionColor : theirReactionColor);
                chip.setOnClickListener(
                        v -> {
                            if (oneOfOurs.isPresent()) {
                                if (emoji instanceof EmojiSearch.CustomEmoji) {
                                    onCustomReactionRemove.accept(oneOfOurs.get());
                                } else {
                                    onModifiedReactions.accept(
                                        ImmutableSet.copyOf(
                                                Collections2.filter(
                                                        aggregated.ourReactions,
                                                        r -> !r.equals(emoji.toString()))));
                                }
                            } else {
                                if (emoji instanceof EmojiSearch.CustomEmoji) {
                                    onCustomReaction.accept((EmojiSearch.CustomEmoji) emoji);
                                } else {
                                    onModifiedReactions.accept(
                                        new ImmutableSet.Builder<String>()
                                                .addAll(aggregated.ourReactions)
                                                .add(emoji.toString())
                                                .build());
                                }
                            }
                        });
                chip.setOnLongClickListener(v -> onDetailsClicked.apply(reaction));
            }
            if (addReaction != null) {
                final Chip chip = chipAt(chipGroup, index, size, corner, largeFont);
                chip.setChipIconResource(R.drawable.ic_add_reaction_24dp);
                chip.setChipBackgroundColor(theirReactionColor);
                chip.setChipIconTint(
                        MaterialColors.getColorStateListOrNull(
                                context,
                                com.google.android.material.R.attr.colorOnSurface));
                chip.setOnClickListener(v -> addReaction.run());
                // this slot may have been a reaction a moment ago, which had one
                chip.setOnLongClickListener(null);
            }
        }
    }

    /**
     * The chip at {@code index}, reused when the group already has one there and built when it does
     * not. Only this class puts children in these groups, so everything in one is a chip.
     *
     * <p>A reused chip is wiped back to blank first. It may have been carrying a different reaction
     * or the add button, and {@link EmojiSearch.Emoji#setupChip} appends the count to whatever text
     * it finds, so leftover text would accumulate.
     */
    private static Chip chipAt(
            final ChipGroup chipGroup,
            final int index,
            final int size,
            final float corner,
            final boolean largeFont) {
        final Chip chip;
        if (index < chipGroup.getChildCount()) {
            chip = (Chip) chipGroup.getChildAt(index);
            chip.setText("");
            chip.setChipIcon(null);
            chip.setChipIconTint(null);
        } else {
            chip = new Chip(chipGroup.getContext());
            chip.setChipMinHeight(size - 22.0f);
            chip.ensureAccessibleTouchTarget(size);
            chip.setLayoutParams(
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size));
            chip.setChipCornerRadius(corner);
            chip.setTextEndPadding(0.0f);
            chip.setTextStartPadding(0.0f);
            chipGroup.addView(chip);
        }
        // Resolving a text appearance is not free, so only when it is not the one already applied.
        if (!Boolean.valueOf(largeFont).equals(chip.getTag())) {
            if (largeFont) {
                chip.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            } else {
                chip.setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            }
            chip.setTag(largeFont);
        }
        return chip;
    }
}
