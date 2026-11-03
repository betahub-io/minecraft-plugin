package io.betahub.minecraft.paper;

import io.betahub.minecraft.core.ReportDraft;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MockBukkit cannot build real dialogs, so the response handling is tested with a fake response view. */
class ReportDialogTest {

    private ServerMock server;
    private org.bukkit.plugin.Plugin plugin;
    private final List<Object[]> submissions = new ArrayList<>();
    private final ReportDialog.SubmitHandler handler = (player, draft) -> submissions.add(new Object[]{player, draft});

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void responseBecomesNormalizedDraft() {
        ReportDraft draft = ReportDialog.draftFrom(view(Map.of(
                ReportDialog.DESCRIPTION_KEY, "  Water flows up \r\n", ReportDialog.STEPS_KEY, "1. place water")));

        assertEquals(new ReportDraft("Water flows up", "1. place water"), draft);
    }

    @Test
    void missingFieldsFromModifiedClientBecomeEmpty() {
        assertEquals(new ReportDraft("", ""), ReportDialog.draftFrom(view(Map.of())));
    }

    @Test
    void playerResponseOnMainThreadIsHandledImmediately() {
        PlayerMock player = server.addPlayer();

        ReportDialog.callback(plugin, handler).accept(view(Map.of(ReportDialog.DESCRIPTION_KEY, "Broken door")), player);

        assertEquals(1, submissions.size());
        assertSame(player, submissions.get(0)[0]);
        assertEquals(new ReportDraft("Broken door", ""), submissions.get(0)[1]);
    }

    @Test
    void responseFromOtherThreadIsMovedToMainThread() throws InterruptedException {
        PlayerMock player = server.addPlayer();
        Thread network = new Thread(() ->
                ReportDialog.callback(plugin, handler).accept(view(Map.of(ReportDialog.DESCRIPTION_KEY, "Lag")), player));
        network.start();
        network.join();
        assertTrue(submissions.isEmpty());

        server.getScheduler().performOneTick();

        assertEquals(1, submissions.size());
    }

    @Test
    void nonPlayerAudienceIsIgnored() {
        ReportDialog.callback(plugin, handler).accept(view(Map.of(ReportDialog.DESCRIPTION_KEY, "x")), Audience.empty());

        assertTrue(submissions.isEmpty());
    }

    private static DialogResponseView view(Map<String, String> texts) {
        return new DialogResponseView() {
            @Override
            public BinaryTagHolder payload() {
                return null;
            }

            @Override
            public String getText(String key) {
                return texts.get(key);
            }

            @Override
            public Boolean getBoolean(String key) {
                return null;
            }

            @Override
            public Float getFloat(String key) {
                return null;
            }
        };
    }
}
