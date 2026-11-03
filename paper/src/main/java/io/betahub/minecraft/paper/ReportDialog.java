package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.ReportDraft;
import io.betahub.minecraft.core.ReportLimits;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything that touches Paper's dialog API, which is still marked experimental.
 * If a Paper update changes that API, this is the only class to fix.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ReportDialog {

    static final String DESCRIPTION_KEY = "description";
    static final String STEPS_KEY = "steps";
    /**
     * How long the Submit button stays usable after the dialog opened. Generous, because an expired button
     * closes the dialog without any feedback and the player's text is lost. Single use plus the cooldown
     * check on submit already prevent abuse.
     */
    static final Duration RESPONSE_LIFETIME = Duration.ofHours(1);
    private static final int SINGLE_USE = 1;
    private static final int WIDTH = 300;
    private static final int DESCRIPTION_HEIGHT = 100;
    private static final int STEPS_HEIGHT = 75;
    /** {@code null} lets the player type as many lines as {@code maxLength} allows. */
    private static final Integer UNLIMITED_LINES = null;

    /** Texts and limits for one dialog. {@code error} is {@code null} unless a previous attempt was invalid. */
    public record Content(Component title, Component body, Component error, Component descriptionLabel,
                          Component stepsLabel, Component submit, Component cancel, ReportLimits limits,
                          ReportDraft initial) {

        public static Content of(Messages messages, ReportLimits limits, ReportDraft initial, Component error) {
            return new Content(
                    messages.get(Messages.Key.DIALOG_TITLE),
                    messages.get(Messages.Key.DIALOG_BODY),
                    error,
                    messages.get(Messages.Key.DIALOG_DESCRIPTION_LABEL),
                    messages.get(Messages.Key.DIALOG_STEPS_LABEL),
                    messages.get(Messages.Key.DIALOG_SUBMIT),
                    messages.get(Messages.Key.DIALOG_CANCEL),
                    limits,
                    initial);
        }
    }

    /** Called on the main thread with what the player typed. */
    @FunctionalInterface
    public interface SubmitHandler {
        void submit(Player player, ReportDraft draft);
    }

    /** Shows a dialog to a player. Exists so tests can replace the real dialog. */
    @FunctionalInterface
    public interface Opener {
        void open(Player player, Content content, SubmitHandler onSubmit);
    }

    private ReportDialog() {
    }

    public static Opener opener(Plugin plugin) {
        return (player, content, onSubmit) -> player.showDialog(build(content, callback(plugin, onSubmit)));
    }

    public static Dialog build(Content content, DialogActionCallback onSubmit) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(content.body(), WIDTH));
        if (content.error() != null) {
            body.add(DialogBody.plainMessage(content.error(), WIDTH));
        }
        ReportLimits limits = content.limits();
        List<DialogInput> inputs = List.of(
                textInput(DESCRIPTION_KEY, content.descriptionLabel(), limits.descriptionMaxLength(),
                        content.initial().description(), DESCRIPTION_HEIGHT),
                textInput(STEPS_KEY, content.stepsLabel(), limits.stepsMaxLength(),
                        content.initial().steps(), STEPS_HEIGHT));

        ActionButton submit = ActionButton.builder(content.submit())
                .action(DialogAction.customClick(onSubmit, ClickCallback.Options.builder()
                        .uses(SINGLE_USE)
                        .lifetime(RESPONSE_LIFETIME)
                        .build()))
                .build();
        // No action: Cancel just closes the dialog.
        ActionButton cancel = ActionButton.builder(content.cancel()).build();

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(content.title())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(submit, cancel)));
    }

    /** Turns the dialog response into a draft and hands it to {@code handler} on the main thread. */
    static DialogActionCallback callback(Plugin plugin, SubmitHandler handler) {
        return (view, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            ReportDraft draft = draftFrom(view);
            if (plugin.getServer().isPrimaryThread()) {
                handler.submit(player, draft);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        handler.submit(player, draft);
                    }
                });
            }
        };
    }

    static ReportDraft draftFrom(DialogResponseView view) {
        return new ReportDraft(view.getText(DESCRIPTION_KEY), view.getText(STEPS_KEY));
    }

    private static TextDialogInput textInput(String key, Component label, int maxLength, String initial, int height) {
        return DialogInput.text(key, label)
                .width(WIDTH)
                .maxLength(maxLength)
                // The client refuses an initial value longer than maxLength.
                .initial(initial.length() > maxLength ? initial.substring(0, maxLength) : initial)
                .multiline(TextDialogInput.MultilineOptions.create(UNLIMITED_LINES, height))
                .build();
    }
}
