package com.creativemd.littletiles.common.packet;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.creativecore.common.utils.ColorUtils;
import com.creativemd.creativecore.common.utils.HashMapList;
import com.creativemd.creativecore.common.utils.TickUtils;
import com.creativemd.creativecore.common.utils.WorldUtils;
import com.creativemd.creativecore.core.CreativeCoreClient;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.common.blocks.BlockTile;
import com.creativemd.littletiles.common.blocks.ISpecialBlockSelector;
import com.creativemd.littletiles.common.blocks.BlockTile.TEResult;
import com.creativemd.littletiles.common.events.LittleEvent;
import com.creativemd.littletiles.common.items.ItemBlockTiles;
import com.creativemd.littletiles.common.items.ItemColorTube;
import com.creativemd.littletiles.common.items.ItemLittleChisel;
import com.creativemd.littletiles.common.items.ItemRubberMallet;
import com.creativemd.littletiles.common.items.ItemTileContainer;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.utils.LittleTile;
import com.creativemd.littletiles.common.utils.LittleTileBlock;
import com.creativemd.littletiles.common.utils.LittleTileBlockColored;
import com.creativemd.littletiles.common.utils.LittleTile.LittleTilePosition;
import com.creativemd.littletiles.common.utils.small.LittleTileBox;
import com.creativemd.littletiles.common.utils.small.LittleTileCoord;
import com.creativemd.littletiles.common.utils.small.LittleTileVec;
import com.creativemd.littletiles.utils.TileList;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class LittleBlockPacket extends CreativeCorePacket{
	
	public static enum BlockPacketAction {
		
		ACTIVATED(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack, EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				tile.onBlockActivated(player.world, pos, player.world.getBlockState(pos), player, EnumHand.MAIN_HAND, player.getHeldItem(EnumHand.MAIN_HAND), moving.sideHit, (float)moving.hitVec.xCoord, (float)moving.hitVec.yCoord, (float)moving.hitVec.zCoord);
				
				BlockTile.cancelNext = true;
			}
		},
		DESTROY(false) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack, EntityPlayer player,
					RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				LittleTileBox box = null;
				
				if(stack != null && stack.getItem() instanceof ISpecialBlockSelector)
				{
					box = ((ISpecialBlockSelector) stack.getItem()).getBox(te, tile, te.getPos(), player, moving);
					if(box != null)
					{
						te.removeBoxFromTiles(box);
						if(!player.capabilities.isCreativeMode)
						{
							tile.boundingBoxes.clear();
							tile.boundingBoxes.add(box.copy());
							WorldUtils.dropItem(player, tile.getDrops());
						}
					}
				}
				if(box == null)
				{
					tile.destroy();
					if(!player.capabilities.isCreativeMode)
						WorldUtils.dropItem(player.world, tile.getDrops(), pos);
				}
				
				world.playSound((EntityPlayer)null, pos, tile.getSound().getBreakSound(), SoundCategory.BLOCKS, (tile.getSound().getVolume() + 1.0F) / 2.0F, tile.getSound().getPitch() * 0.8F);
			}
		},
		SAW(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack,
					EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				int side = nbt.getInteger("side");
				EnumFacing direction = EnumFacing.getFront(side);
				if(tile.canSawResizeTile(direction, player))
				{
					LittleTileBox box = null;
					if(player.isSneaking())
						box = tile.boundingBoxes.get(0).shrink(direction);
					else
						box = tile.boundingBoxes.get(0).expand(direction);
					
					if(box.isValidBox())
					{
						double ammount = tile.boundingBoxes.get(0).getSize().getPercentVolume()-box.getSize().getPercentVolume();
						boolean success = false;
						if(player.isSneaking())
						{
							if(ItemTileContainer.addBlock(player, ((LittleTileBlock)tile).getBlock(), ((LittleTileBlock)tile).getMeta(), ammount))
								success = true;
						}else{
							if(ItemTileContainer.drainBlock(player, ((LittleTileBlock)tile).getBlock(), ((LittleTileBlock)tile).getMeta(), -ammount))
								success = true;
						}
						
						if(player.capabilities.isCreativeMode || success)
						{
							if(box.isBoxInsideBlock() && te.isSpaceForLittleTile(box.getBox(), tile))
							{
								tile.boundingBoxes.set(0, box);
								tile.updateCorner();
								te.updateBlock();
							}else if(!box.isBoxInsideBlock()){
								box = box.createOutsideBlockBox(direction);
								BlockPos newPos = pos.offset(direction);
								IBlockState state = world.getBlockState(newPos);
								TileEntityLittleTiles littleTe = null;
								TileEntity newTE = world.getTileEntity(newPos);
								if(newTE instanceof TileEntityLittleTiles)
									littleTe = (TileEntityLittleTiles) newTE;
								if(state.getMaterial().isReplaceable())
								{
									//new TileEntityLittleTiles();
									world.setBlockState(newPos, LittleTiles.blockTile.getDefaultState());
									littleTe = (TileEntityLittleTiles) world.getTileEntity(newPos);
								}
								if(littleTe != null)
								{
									LittleTile newTile = tile.copy();
									newTile.boundingBoxes.clear();
									newTile.boundingBoxes.add(box);
									newTile.te = littleTe;
									
									if(littleTe.isSpaceForLittleTile(box))
									{
										newTile.place();
										//littleTe.addTile(newTile);
										littleTe.updateBlock();
									}
								}
							}
						}
					}
				}
			}
		},
		COLOR_TUBE(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack,
					EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				if((tile.getClass() == LittleTileBlock.class || tile instanceof LittleTileBlockColored))
				{
					int color = nbt.getInteger("color");
					
					if(player.isSneaking())
					{
						color = ColorUtils.WHITE;
						if(tile instanceof LittleTileBlockColored)
							color = ((LittleTileBlockColored) tile).color;
						ItemColorTube.setColor(player.getHeldItemMainhand(), color);
					}else{
						tile.te.preventUpdate = true;
						LittleTile newTile = LittleTileBlockColored.setColor((LittleTileBlock) tile, color);
						if(newTile != null)
						{
							tile.te.removeTile(tile);
							tile.te.addTile(newTile);
						}
						if(tile.isStructureBlock)
						{
							newTile.isStructureBlock = true;
							newTile.structure.removeTile(tile);
							newTile.structure.addTile(newTile);
							if(tile.isMainBlock)
								newTile.structure.setMainTile(newTile);
							newTile.structure.getMainTile().te.updateBlock();
						}
						tile.te.preventUpdate = false;
						te.updateTiles();
					}
				}
			}
		},
		RUBBER_MALLET(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack,
					EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				int side = nbt.getInteger("side");
				EnumFacing direction = EnumFacing.getFront(side).getOpposite();
				boolean push = !player.isSneaking();
				if(!push)
					direction = direction.getOpposite();
				if(tile.canBeMoved(direction))
				{
					if(tile.isStructureBlock)
					{
						if(tile.checkForStructure())
						{
							LittleStructure structure = tile.structure;
							if(structure.hasLoaded())
							{
								HashMapList<TileEntityLittleTiles, LittleTile> tiles = structure.copyOfTiles();
								for (Iterator<LittleTile> iterator = tiles.iterator(); iterator.hasNext();)
								{
									LittleTile tileOfCopy = iterator.next();
									if(!ItemRubberMallet.moveTile(tileOfCopy.te, direction, tileOfCopy, true, push))
										return ;
								}
								
								for (Iterator<LittleTile> iterator = tiles.iterator(); iterator.hasNext();)
								{
									LittleTile tileOfCopy = iterator.next();
									ItemRubberMallet.moveTile(tileOfCopy.te, direction, tileOfCopy, false, push);
								}
								
								structure.combineTiles();
								structure.selectMainTile();
								structure.moveStructure(direction);
							}else
								player.sendMessage(new TextComponentString("Cannot move structure (not all tiles are loaded)."));
						}
					}else
						if(ItemRubberMallet.moveTile(te, direction, tile, false, push))
							te.updateTiles();												
				}
			}
		},
		GLOWING(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack,
					EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				if(stack.getItem() == Items.GLOWSTONE_DUST && player.isSneaking())
				{
					if(!player.isCreative())
					{
						if(tile.glowing){
							if(!player.inventory.addItemStackToInventory(new ItemStack(Items.GLOWSTONE_DUST)))
								player.dropItem(new ItemStack(Items.GLOWSTONE_DUST), true);
						}else
							stack.shrink(1);
					}
					if(tile.glowing)
						player.playSound(SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, 1.0F, 1.0F);
					else
						player.playSound(SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, 1.0F, 1.0F);
					tile.glowing = !tile.glowing;
					te.updateLighting();
				}
			}
		},
		CHISEL(true) {
			@Override
			public void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack,
					EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt) {
				if(player.isSneaking() && tile instanceof LittleTileBlock)
					ItemLittleChisel.setBlockState(stack, ((LittleTileBlock) tile).getBlockState());
			}
		};
		
		public final boolean rightClick;
		
		private BlockPacketAction(boolean rightClick) {
			this.rightClick = rightClick;
		}
		
		public abstract void action(World world, TileEntityLittleTiles te, LittleTile tile, ItemStack stack, EntityPlayer player, RayTraceResult moving, BlockPos pos, NBTTagCompound nbt);
	}
	
	public BlockPos blockPos;
	public Vec3d pos;
	public Vec3d look;
	public BlockPacketAction action;
	public NBTTagCompound nbt;
	
	public LittleBlockPacket()
	{
		
	}
	
	public LittleBlockPacket(BlockPos blockPos, EntityPlayer player, BlockPacketAction action)
	{
		this(blockPos, player, action, new NBTTagCompound());
	}
	
	public LittleBlockPacket(BlockPos blockPos, EntityPlayer player, BlockPacketAction action, NBTTagCompound nbt)
	{
		this.blockPos = blockPos;
		this.action = action;
		this.pos = player.getPositionEyes(TickUtils.getPartialTickTime());
		double d0 = player.capabilities.isCreativeMode ? 5.0F : 4.5F;
		Vec3d look = player.getLook(TickUtils.getPartialTickTime());
		this.look = pos.addVector(look.xCoord * d0, look.yCoord * d0, look.zCoord * d0);
		this.nbt = nbt;
	}
	
	@Override
	public void writeBytes(ByteBuf buf) {
		writePos(buf, blockPos);
		writeVec3d(pos, buf);
		writeVec3d(look, buf);
		buf.writeInt(action.ordinal());
		writeNBT(buf, nbt);
	}
	
	@Override
	public void readBytes(ByteBuf buf) {
		blockPos = readPos(buf);
		pos = readVec3d(buf);
		look = readVec3d(buf);
		action = BlockPacketAction.values()[buf.readInt()];
		nbt = readNBT(buf);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void executeClient(EntityPlayer player) {
		
	
	}
	
	private static Method loadWorldEditEvent()
	{
		try
		{
			Class clazz = Class.forName("com.sk89q.worldedit.forge.ForgeWorldEdit");
			worldEditInstance = clazz.getField("inst").get(null);
			return clazz.getMethod("onPlayerInteract", PlayerInteractEvent.class);
		}catch(Exception e){
			
		}
		return null;
	}
	
	private static Method WorldEditEvent = loadWorldEditEvent();
	private static Object worldEditInstance = null;
	
	public static boolean isAllowedToInteract(EntityPlayer player, BlockPos pos, boolean rightClick, EnumFacing facing)
	{
		if(WorldEditEvent != null)
		{
			PlayerInteractEvent event = rightClick ? new PlayerInteractEvent.RightClickBlock(player, EnumHand.MAIN_HAND, pos, facing, new Vec3d(pos)) : new PlayerInteractEvent.LeftClickBlock(player, pos, facing, new Vec3d(pos));
			try {
				if(worldEditInstance == null)
				{
					loadWorldEditEvent();
				}
				WorldEditEvent.invoke(worldEditInstance, event);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
			if(event.isCanceled())
				return false;
		}
		
		return !player.getServer().isBlockProtected(player.world, pos, player);
		
	}
	
	@Override
	public void executeServer(EntityPlayer player) {
		TileEntity tileEntity = player.world.getTileEntity(blockPos);
		World world = player.world;
		if(tileEntity instanceof TileEntityLittleTiles)
		{
			TileEntityLittleTiles te = (TileEntityLittleTiles) tileEntity;
			LittleTile tile = te.getFocusedTile(pos, look);
			
			if(!isAllowedToInteract(player, blockPos, action.rightClick, EnumFacing.EAST))
			{
				te.updateBlock();
				return ;
			}
			
			if(tile != null)
			{
				ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
				RayTraceResult moving = te.getMoving(pos, look);
				action.action(world, te, tile, stack, player, moving, blockPos, nbt);
			}
		}else if(action == BlockPacketAction.ACTIVATED)
			LittleEvent.cancelNext = true;
	}
	
}
