package com.creativemd.littletiles.common.packet;

import java.util.Iterator;
import java.util.UUID;

import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.events.LittleDoorHandler;
import com.google.common.base.Predicate;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class LittleEntityRequestPacket extends CreativeCorePacket {
	
	public LittleEntityRequestPacket() {
		
	}
	
	public UUID uuid;
	public NBTTagCompound nbt;
	public boolean enteredAsChild;
	
	public LittleEntityRequestPacket(UUID uuid, NBTTagCompound nbt, boolean enteredAsChild) {
		this.uuid = uuid;
		this.nbt = nbt;
		this.enteredAsChild = enteredAsChild;
	}
	
	@Override
	public void writeBytes(ByteBuf buf) {
		writeString(buf, uuid.toString());
		writeNBT(buf, nbt);
		buf.writeBoolean(enteredAsChild);
	}
	
	@Override
	public void readBytes(ByteBuf buf) {
		uuid = UUID.fromString(readString(buf));
		nbt = readNBT(buf);
		enteredAsChild = buf.readBoolean();
	}
	
	@Override
	public void executeClient(EntityPlayer player) {
		EntityAnimation animation = LittleDoorHandler.getHandler(true).findDoor(uuid);
		if (animation != null) {
			updateAnimation(animation);
			return;
		}
		
		for (Iterator<EntityAnimation> iterator = player.world.getEntities(EntityAnimation.class, new Predicate<EntityAnimation>() {
			
			@Override
			public boolean apply(EntityAnimation input) {
				return true;
			}
			
		}).iterator();iterator.hasNext();) {
			Entity entity = iterator.next();
			if (entity instanceof EntityAnimation && entity.getUniqueID().equals(uuid)) {
				animation = (EntityAnimation) entity;
				updateAnimation(animation);
				if (!animation.isDoorAdded())
					animation.addDoor();
				return;
			}
		}
		System.out.println("Entity not found!");
	}
	
	public void updateAnimation(EntityAnimation animation) {
		animation.isDead = false;
		if (!this.enteredAsChild || animation.enteredAsChild != this.enteredAsChild) {
			animation.readFromNBT(nbt);
			animation.updateTickState();
		}
	}
	
	@Override
	public void executeServer(EntityPlayer player) {
		
	}
	
}
