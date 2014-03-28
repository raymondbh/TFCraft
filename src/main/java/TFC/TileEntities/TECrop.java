package TFC.TileEntities;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import net.minecraft.crash.CrashReportCategory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import TFC.API.TFCOptions;
import TFC.Core.TFC_Climate;
import TFC.Core.TFC_Time;
import TFC.Food.CropIndex;
import TFC.Food.CropManager;
import TFC.Handlers.PacketHandler;

public class TECrop extends TileEntity
{
	public float growth;
	public int cropId;
	private long growthTimer;//Tracks the time since the plant was planted
	private long plantedTime;//Tracks the time when the plant was planted
	private byte sunLevel;

	public TECrop()
	{
		growth = 0.1f;
		plantedTime = TFC_Time.getTotalTicks();
		growthTimer = TFC_Time.getTotalTicks();
		sunLevel = 5;
	}

	private boolean checkedSun = false;
	@Override
	public void updateEntity()
	{
		Random R = new Random();
		if(!worldObj.isRemote)
		{
			float timeMultiplier = 360/TFC_Time.daysInYear;
			sunLevel--;

			CropIndex crop = CropManager.getInstance().getCropFromId(cropId);
			long time = TFC_Time.getTotalTicks();

			if(growthTimer < time && sunLevel > 0)
			{
				if(crop.needsSunlight && (worldObj.getBlockLightValue(xCoord, yCoord, zCoord) > 11 || worldObj.canBlockSeeTheSky(xCoord, yCoord, zCoord)))
				{
					sunLevel++;
					if(sunLevel > 30)
						sunLevel = 30;
				}

				TileEntityFarmland tef = null;
				TileEntity te = worldObj.getTileEntity(xCoord, yCoord-1, zCoord);
				if(te != null && te instanceof TileEntityFarmland)
					tef = (TileEntityFarmland) te;

				float ambientTemp = TFC_Climate.getHeightAdjustedTempSpecificDay(TFC_Time.getDayOfYearFromTick(growthTimer), xCoord, yCoord, zCoord);
				float tempAdded = 0;

				if(!crop.dormantInFrost && ambientTemp < crop.minGrowthTemp)
					tempAdded = -0.03f * (crop.minGrowthTemp - ambientTemp);
				else if(crop.dormantInFrost && ambientTemp < crop.minGrowthTemp)
				{
					if(growth > 1)
						tempAdded = -0.03f * (crop.minGrowthTemp - ambientTemp);
				}
				else if(ambientTemp < 28)
					tempAdded = ambientTemp* 0.00035f;
				else if(ambientTemp < 37)
					tempAdded = (28-(ambientTemp-28))* 0.0003f;

				if(!crop.dormantInFrost && ambientTemp < crop.minAliveTemp)
					worldObj.setBlockToAir(xCoord, yCoord, zCoord);
				else if(crop.dormantInFrost && ambientTemp < crop.minAliveTemp)
					if(growth > 1)
						worldObj.setBlockToAir(xCoord, yCoord, zCoord);

				int nutriType = crop.cycleType;
				int nutri = tef != null ? tef.nutrients[nutriType] : 18000;
				int fert = tef != null ? tef.nutrients[3] : 0;
				int soilMax = tef != null ? tef.getSoilMax() : 18000;
				//waterBoost only helps if you are playing on a longer than default year length.
				float waterBoost = TFC.Blocks.BlockFarmland.isWaterNearby(worldObj, xCoord, yCoord, zCoord) ? 0.1f : 0;

				//Allow the fertilizer to make up for lost nutrients
				nutri = Math.min(nutri+fert, (int)(soilMax*1.25f));

				float nutriMult = (0.2f + ((float)nutri/(float)soilMax) * 0.5f) + waterBoost;

				if(tef != null)
				{
					if(tef.nutrients[nutriType] > 0)
						tef.DrainNutrients(nutriType, crop.nutrientUsageMult);
					//Drain Fertilizer
					if(tef.nutrients[3] > 0)
						tef.DrainNutrients(3, crop.nutrientUsageMult);
				}

				float growthRate = (((crop.numGrowthStages/(crop.growthTime*TFC_Time.timeRatio))+tempAdded)*nutriMult) * timeMultiplier;

				int oldGrowth = (int) Math.floor(growth);

				growth += growthRate;

				if(oldGrowth < (int) Math.floor(growth))
					//TODO Send update packet
					//this.broadcastPacketInRange(createCropUpdatePacket());

				if((TFCOptions.enableCropsDie && (crop.maxLifespan == -1 && growth > crop.numGrowthStages+((float)crop.numGrowthStages/2))) || growth < 0)
					worldObj.setBlockToAir(xCoord, yCoord, zCoord);

				growthTimer += (R.nextInt(2)+23)*TFC_Time.hourLength;
			}
			else if(crop.needsSunlight && sunLevel <= 0)
				worldObj.setBlockToAir(xCoord, yCoord, zCoord);

			if(worldObj.isRaining() && TFC_Climate.getHeightAdjustedTemp(xCoord, yCoord, zCoord) < 0)
				if(!crop.dormantInFrost || growth > 1)
					worldObj.setBlockToAir(xCoord, yCoord, zCoord);
		}
	}

	public float getEstimatedGrowth(CropIndex crop)
	{
		return ((float)crop.numGrowthStages/(growthTimer-plantedTime/TFC_Time.dayLength))*1.5f;
	}

	/**
	 * Reads a tile entity from NBT.
	 */
	@Override
	public void readFromNBT(NBTTagCompound par1NBTTagCompound)
	{
		super.readFromNBT(par1NBTTagCompound);
		growth = par1NBTTagCompound.getFloat("growth");
		cropId = par1NBTTagCompound.getInteger("cropId");
		growthTimer = par1NBTTagCompound.getLong("growthTimer");
		plantedTime = par1NBTTagCompound.getLong("plantedTime");
	}

	/**
	 * Writes a tile entity to NBT.
	 */
	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setFloat("growth", growth);
		nbt.setInteger("cropId", cropId);
		nbt.setLong("growthTimer", growthTimer);
		nbt.setLong("plantedTime", plantedTime);
	}

	@Override
	public Packet getDescriptionPacket()
	{
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt)
	{
		readFromNBT(pkt.func_148857_g());
	}

}