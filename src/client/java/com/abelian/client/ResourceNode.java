package com.abelian.client;

import net.minecraft.resources.ResourceLocation;

/** One raw resource provider, without a duplicate file browser tree. */
public record ResourceNode(String packId, ResourceLocation location) {}
