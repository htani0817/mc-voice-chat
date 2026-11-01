package jp.mc.voicechat.client;

import jp.mc.voicechat.net.VoiceStatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * パスワード付きのパーティーに入るときに、パスワードを聞く画面。
 */
final class PartyPasswordScreen extends Screen {
    private final Screen parent;
    private final VoiceStatePayload.Party party;
    private EditBox password;

    PartyPasswordScreen(Screen parent, VoiceStatePayload.Party party) {
        super(Component.translatable("mc_voice_chat.settings.party.password_title", party.name()));
        this.parent = parent;
        this.party = party;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 20;
        password = addRenderableWidget(new EditBox(font, cx - 100, y, 200, 20, Component.translatable("mc_voice_chat.settings.party.password")));
        password.setMaxLength(VoiceSettingsScreen.MAX_PASSWORD);
        password.setHint(Component.translatable("mc_voice_chat.settings.party.password"));
        setInitialFocus(password);
        addRenderableWidget(Button.builder(Component.translatable("mc_voice_chat.settings.party.join"), b -> submit())
                .bounds(cx - 100, y + 28, 96, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(cx + 4, y + 28, 96, 20).build());
    }

    private void submit() {
        VoiceClient.get().joinParty(party.id(), password.getValue());
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Enter でも参加する
        if (event.isConfirmation() && password.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
    }
}
