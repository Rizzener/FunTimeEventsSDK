package net.funtimeevents.poc.fixture.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

/** Fixture-only way to serialize deterministic offline TAB entries via vanilla codecs. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoPacketWriter {
    @Mutable @Accessor("entries") void fte$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
