package crazylimits.dragonsworn.mc.breath.client;

import net.minecraft.client.Minecraft;

/** Client-only state for code that also runs on servers: call it only when the level is client-side. */
public final class ClientGame {
	private ClientGame() {}

	/** The singleplayer game is paused (pause menu open, or the window lost focus). */
	public static boolean paused() {
		return Minecraft.getInstance().isPaused();
	}
}
