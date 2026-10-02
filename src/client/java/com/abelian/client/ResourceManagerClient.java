package com.abelian.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

public class ResourceManagerClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.resourcemanager";
    private static KeyMapping openKey;
    private static boolean keyWasDown;

    @Override
    public void onInitializeClient() {
        ResourceManagerConfig.replaceInstance(ResourceManagerConfig.load());
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcemanager.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                open(client);
            }
            // The game only raises key bindings while no screen is open, so poll the bound key as well to
            // reach the title and pause screens, where a broken pack is usually noticed first.
            boolean down = isKeyDown(client);
            if (down && !keyWasDown) {
                open(client);
            }
            keyWasDown = down;
        });
    }

    private static boolean isKeyDown(Minecraft client) {
        InputConstants.Key key = openKey.getDefaultKey();
        InputConstants.Type type = key.getType();
        long window = client.getWindow().getWindow();
        if (type == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        if (type == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return openKey.isDown();
    }

    private static void open(Minecraft client) {
        Screen current = client.screen;
        if (current instanceof ResourceManagerScreen) {
            return;
        }
        if (current == null || current instanceof TitleScreen || current instanceof PauseScreen) {
            client.setScreen(new ResourceManagerScreen(current));
        }
    }
}
