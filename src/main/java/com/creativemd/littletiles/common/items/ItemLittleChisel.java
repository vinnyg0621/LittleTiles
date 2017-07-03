package com.creativemd.littletiles.common.items;

import java.util.ArrayList;
import java.util.List;

import com.creativemd.creativecore.client.rendering.RenderCubeObject;
import com.creativemd.creativecore.client.rendering.model.ICreativeRendered;
import com.creativemd.creativecore.client.rendering.model.IExtendedCreativeRendered;
import com.creativemd.creativecore.common.packet.PacketHandler;
import com.creativemd.creativecore.common.utils.WorldUtils;
import com.creativemd.creativecore.gui.container.SubContainer;
import com.creativemd.creativecore.gui.container.SubGui;
import com.creativemd.creativecore.gui.opener.GuiHandler;
import com.creativemd.creativecore.gui.opener.IGuiCreator;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.common.blocks.BlockTile;
import com.creativemd.littletiles.common.blocks.ILittleTile;
import com.creativemd.littletiles.common.container.SubContainerChisel;
import com.creativemd.littletiles.common.container.SubContainerHammer;
import com.creativemd.littletiles.common.container.SubContainerWrench;
import com.creativemd.littletiles.common.gui.SubGuiChisel;
import com.creativemd.littletiles.common.items.ItemTileContainer.BlockEntry;
import com.creativemd.littletiles.common.items.shapes.ChiselShape;
import com.creativemd.littletiles.common.packet.LittleBlockPacket;
import com.creativemd.littletiles.common.packet.LittleBlockPacket.BlockPacketAction;
import com.creativemd.littletiles.common.structure.LittleStructure;
import com.creativemd.littletiles.common.utils.LittleTile;
import com.creativemd.littletiles.common.utils.LittleTileBlock;
import com.creativemd.littletiles.common.utils.LittleTilePreview;
import com.creativemd.littletiles.common.utils.PlacementHelper.PositionResult;
import com.creativemd.littletiles.common.utils.small.LittleTileBox;
import com.creativemd.littletiles.common.utils.small.LittleTileSize;
import com.creativemd.littletiles.common.utils.small.LittleTileVec;
import com.creativemd.littletiles.utils.PlacePreviewTile;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import scala.collection.parallel.ParIterableLike.Min;

public class ItemLittleChisel extends Item implements IGuiCreator, ICreativeRendered, ILittleTile {
	
	public ItemLittleChisel()
	{
		setCreativeTab(LittleTiles.littleTab);
		hasSubtypes = true;
		setMaxStackSize(1);
	}
	
	@Override
	public float getStrVsBlock(ItemStack stack, IBlockState state)
    {
        return 0F;
    }
	
	@Override
	@SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean advanced)
	{
		ChiselShape shape = getShape(stack);
		list.add("shape: " + shape.key);
		shape.addExtraInformation(stack.getTagCompound(), list);
	}
	
	@Override
	public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
		if(hand == EnumHand.MAIN_HAND && player.isSneaking())
		{
			IBlockState state = worldIn.getBlockState(pos);
			if(SubContainerHammer.isBlockValid(state.getBlock()))
				setBlockState(player.getHeldItemMainhand(), state);
			else if(state.getBlock() instanceof BlockTile)
				PacketHandler.sendPacketToServer(new LittleBlockPacket(pos, player, BlockPacketAction.CHISEL, new NBTTagCompound()));
			return EnumActionResult.SUCCESS;
		}
        return EnumActionResult.PASS;
    }
	
	@SideOnly(Side.CLIENT)
	public static boolean isClicking;
	
	@SideOnly(Side.CLIENT)
	public static boolean hasClicked;
	
	public static LittleTileVec min;
	
	@SideOnly(Side.CLIENT)
	public static LittleTileVec lastMax;
	
	public static void placePreviews(World world, LittleTileVec min, LittleTileVec max, EntityPlayer player, ItemStack stack, EnumFacing facing)
	{
		ChiselShape shape = getShape(stack);
		List<LittleTileBox> boxes = shape.getBoxes(min, max, player, stack.getTagCompound(), false);
		if(boxes.size() > 0)
		{
			IBlockState state = getBlockState(stack);
			Block block = state.getBlock();
			int meta = block.getMetaFromState(state);
			
			boolean canPlace = true;
			if(!player.isCreative())
			{
				double volume = 0;
				for (int i = 0; i < boxes.size(); i++) {
					volume += boxes.get(i).getSize().getPercentVolume();
				}
				ArrayList<BlockEntry> ingredients = new ArrayList<>();
				ingredients.add(new BlockEntry(block, meta, volume));
				ArrayList<BlockEntry> remaining = new ArrayList<>();
				if(canPlace = SubContainerWrench.drainIngridients(ingredients, player.inventory, false, remaining, false))
				{
					remaining.clear();
					SubContainerWrench.drainIngridients(ingredients, player.inventory, true, remaining, false);
					for (int i = 0; i < remaining.size(); i++) {
						if(!ItemTileContainer.addBlock(player, remaining.get(i).block, remaining.get(i).meta, remaining.get(i).value))
							WorldUtils.dropItem(player, remaining.get(i).getTileItemStack());
					}
				}
			}
			
			if(!canPlace)
			{
				if(!world.isRemote)
					player.sendStatusMessage(new TextComponentTranslation("Not enough materials!"), true);
				return ;
			}
		
		
			BlockPos pos = boxes.get(0).getMinVec().getBlockPos();
			LittleTileVec offset = new LittleTileVec(pos);
			LittleTileVec zero = new LittleTileVec(0, 0, 0);
			ArrayList<PlacePreviewTile> previews = new ArrayList<>();
			
			LittleTileBlock tile = new LittleTileBlock(block, meta);
			
			for (int i = 0; i < boxes.size(); i++) {
				tile.boundingBoxes.clear();
				boxes.get(i).subOffset(offset);;
				tile.boundingBoxes.add(boxes.get(i));
				previews.add(tile.getPreviewTile().getPlaceableTile(null, true, zero));
			}
			
			ArrayList<LittleTile> unplaceableTiles = new ArrayList<LittleTile>();
			
			ItemBlockTiles.placeTiles(world, player, previews, null, pos, stack, unplaceableTiles, true, facing);
			
			if(!world.isRemote)
			{
				for (int j = 0; j < unplaceableTiles.size(); j++) {
					if(!(unplaceableTiles.get(j) instanceof LittleTileBlock) && !ItemTileContainer.addBlock(player, ((LittleTileBlock)unplaceableTiles.get(j)).getBlock(), ((LittleTileBlock)unplaceableTiles.get(j)).getMeta(), (float)((LittleTileBlock)unplaceableTiles.get(j)).getPercentVolume()))
						WorldUtils.dropItem(world, unplaceableTiles.get(j).getDrops(), pos);
				}
			}
		}
	}
	
	@Override
	public boolean canDestroyBlockInCreative(World world, BlockPos pos, ItemStack stack, EntityPlayer player)
    {
        return false;
    }
	
	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand)
    {
		if(hand == EnumHand.OFF_HAND)
			return new ActionResult(EnumActionResult.PASS, player.getHeldItem(hand)); 
		if(!world.isRemote)
			GuiHandler.openGuiItem(player, world);
        return new ActionResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
	
	@Override
	@SideOnly(Side.CLIENT)
	public SubGui getGui(EntityPlayer player, ItemStack stack, World world, BlockPos pos, IBlockState state) {
		return new SubGuiChisel(stack);
	}

	@Override
	public SubContainer getContainer(EntityPlayer player, ItemStack stack, World world, BlockPos pos,
			IBlockState state) {
		return new SubContainerChisel(player, stack);
	}
	
	public static ChiselShape getShape(ItemStack stack)
	{
		if(!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		return ChiselShape.getShape(stack.getTagCompound().getString("shape"));
	}
	
	public static void setBlockState(ItemStack stack, IBlockState state)
	{
		if(!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		stack.getTagCompound().setInteger("state", Block.getStateId(state));
	}
	
	public static IBlockState getBlockState(ItemStack stack)
	{
		if(!stack.hasTagCompound())
			stack.setTagCompound(new NBTTagCompound());
		
		IBlockState state = Block.getStateById(stack.getTagCompound().getInteger("state"));
		return state.getBlock() instanceof BlockAir ? Blocks.STONE.getDefaultState() : state;			
	}

	@Override
	@SideOnly(Side.CLIENT)
	public List<RenderCubeObject> getRenderingCubes(IBlockState state, TileEntity te, ItemStack stack) {
		return new ArrayList<>();
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void applyCustomOpenGLHackery(ItemStack stack, TransformType cameraTransformType)
	{
		Minecraft mc = Minecraft.getMinecraft();
		GlStateManager.pushMatrix();
		
		IBakedModel model = mc.getRenderItem().getItemModelMesher().getModelManager().getModel(new ModelResourceLocation(LittleTiles.modid + ":chisel_background", "inventory"));
		ForgeHooksClient.handleCameraTransforms(model, cameraTransformType, false);
		
		mc.getRenderItem().renderItem(new ItemStack(Items.PAPER), model);
		
		if(cameraTransformType == TransformType.GUI)
		{
			GlStateManager.scale(0.9, 0.9, 0.9);
			
			IBlockState state = getBlockState(stack);
			ItemStack blockStack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
			model =  mc.getRenderItem().getItemModelMesher().getItemModel(blockStack);
			ForgeHooksClient.handleCameraTransforms(model, cameraTransformType, false);
			
			GlStateManager.disableDepth();
			
			mc.getRenderItem().renderItem(blockStack, model);
			
			GlStateManager.enableDepth();
		}
		
		GlStateManager.popMatrix();
		
	}
	
	@Override
	public boolean hasLittlePreview(ItemStack stack)
	{
		return min != null;
	}
	
	private static LittleTileVec cachedPos;
	private static List<LittleTileBox> cachedShape;
	
	@SideOnly(Side.CLIENT)
	private static EntityPlayer getPlayer()
	{
		return Minecraft.getMinecraft().player;
	}
	
	public static LittleTileBox getBox()
	{
		if(lastMax == null)
			lastMax = min.copy();
		
		return new LittleTileBox(new LittleTileBox(min), new LittleTileBox(lastMax));
	}
	
	@Override
	public List<LittleTilePreview> getLittlePreview(ItemStack stack)
	{
		if(min != null)
		{
			List<LittleTileBox> boxes = null;
			if(cachedPos == null || !cachedPos.equals(lastMax))
			{
				
				ChiselShape shape = getShape(stack);
				LittleTileBox newBox = getBox();
				boxes = shape.getBoxes(newBox.getMinVec(), newBox.getMaxVec(), getPlayer(), stack.getTagCompound(), true);
				cachedPos = lastMax.copy();
				cachedShape = new ArrayList<>(boxes);
			}else
				boxes = cachedShape;
			
			List<LittleTilePreview> previews = new ArrayList<>();
			
			IBlockState state = getBlockState(stack);
			LittleTileBlock tile = new LittleTileBlock(state.getBlock(), state.getBlock().getMetaFromState(state));
			
			for (int i = 0; i < boxes.size(); i++) {
				tile.boundingBoxes.clear();
				tile.boundingBoxes.add(boxes.get(i).copy());
				previews.add(tile.getPreviewTile());
			}
			
			return previews;
		}
		return null;
	}
	
	@Override
	public void saveLittlePreview(ItemStack stack, List<LittleTilePreview> previews) {}
	
	@Override
	public void rotateLittlePreview(ItemStack stack, EnumFacing facing) {}
	
	@Override
	public void flipLittlePreview(ItemStack stack, EnumFacing facing) {}
	
	@Override
	public LittleStructure getLittleStructure(ItemStack stack)
	{
		return null;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public float getPreviewAlphaFactor()
	{
		return 0.4F;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void tickPreview(EntityPlayer player, ItemStack stack, PositionResult position)
	{
		lastMax = position.getAbsoluteVec();
		if(position.facing.getAxisDirection() == AxisDirection.NEGATIVE)
			lastMax.addVec(new LittleTileVec(position.facing));
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldCache()
	{
		return false;
	}
	
	@Override
	public boolean arePreviewsAbsolute()
	{
		return true;
	}
	
}
