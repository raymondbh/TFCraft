package TFC.WorldGen.GenLayers;

import TFC.*;
import net.minecraft.world.gen.layer.IntCache;

public class GenLayerIslandTFC extends GenLayerTFC
{
    public GenLayerIslandTFC(long par1)
    {
        super(par1);
    }

    /**
     * Returns a list of integer values generated by this layer. These may be interpreted as temperatures, rainfall
     * amounts, or biomeList[] indices based on the particular GenLayer subclass.
     */
    public int[] getInts(int par1, int par2, int maxX, int maxZ)
    {
        int[] var5 = IntCache.getIntCache(maxX * maxZ);

        for (int z = 0; z < maxZ; ++z)
        {
            for (int x = 0; x < maxX; ++x)
            {
                this.initChunkSeed((long)(par1 + x), (long)(par2 + z));
                var5[x + z * maxX] = this.nextInt(5) == 0 ? 1 : 0;
            }
        }

        if (par1 > -maxX && par1 <= 0 && par2 > -maxZ && par2 <= 0)
        {
            var5[-par1 + -par2 * maxX] = 1;
        }

        return var5;
    }
}
