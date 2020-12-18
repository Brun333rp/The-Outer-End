package blueduck.outerend.entities;

import blueduck.outerend.registry.BlockRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class DragonflyEntity extends MobEntity {
	public DragonflyEntity(EntityType<? extends MobEntity> type, World worldIn) {
		super(type, worldIn);
		navigator = new FlyingPathNavigator(this, this.world);
	}
	
	public static AttributeModifierMap createModifiers() {
		return MonsterEntity.func_234295_eP_()
				.createMutableAttribute(Attributes.FLYING_SPEED, 1)
				.createMutableAttribute(Attributes.MOVEMENT_SPEED, 0)
				.createMutableAttribute(Attributes.ATTACK_DAMAGE, 0)
				.createMutableAttribute(Attributes.ARMOR, 0)
				.createMutableAttribute(Attributes.MAX_HEALTH, 10).create();
	}
	
	public boolean shouldRepathfind() {
		return this.ticksExisted % 1000 <= 10;
	}
	
	public Path getPath() {
		BlockPos pos = null;
		for (int i = 0; i <= 32; i++) {
			int x1 = (int) this.getPosX() + rand.nextInt(64) - 32;
			int z1 = (int) this.getPosZ() + rand.nextInt(64) - 32;
			int y = world.getHeight(Heightmap.Type.WORLD_SURFACE, x1, z1);
			boolean shouldFind = rand.nextBoolean();
			if (y == 0) continue;
			if (pos != null) {
				shouldFind = shouldFind &&
						(world.getBiome(new BlockPos(x1, y, z1)).getRegistryName().toString().equals("outer_end:azure_forest") ||
								!world.getBiome(pos).getRegistryName().toString().equals("outer_end:azure_forest"))
				;
				shouldFind = shouldFind || (
						world.getBiome(new BlockPos(x1, y, z1)).getRegistryName().toString().equals("outer_end:azure_forest") &&
								!world.getBiome(pos).getRegistryName().toString().equals("outer_end:azure_forest")
				);
			}
			if (shouldFind && pos == null)
				pos = new BlockPos(x1, y, z1);
			int searchDist = 8;
			for (int xOff = -searchDist; xOff <= searchDist; xOff++) {
				int x = x1 + xOff;
				for (int zOff = -searchDist; zOff <= searchDist; zOff++) {
					int z = z1 + zOff;
					int y1 = world.getHeight(Heightmap.Type.WORLD_SURFACE, x1, z1);
					if (world.getBlockState(new BlockPos(x, y1, z).down()).getBlock().equals(BlockRegistry.AZURE_STAMEN.get()))
						if (pos == null || y > pos.getY()) pos = new BlockPos(x, y1, z);
				}
			}
		}
		if (pos == null) {
			int x = (int) this.getPosX()+rand.nextInt(64)-32;
			int z = (int) this.getPosZ()+rand.nextInt(64)-32;
			int y = world.getHeight(Heightmap.Type.WORLD_SURFACE, x, z);
			pos = new BlockPos(x, Math.max(8,y), z);
		}
		return navigator.getPathToPos(pos, 128);
	}
	
	@Override
	public void tick() {
		if (this.getEntityWorld().isRemote) {
			super.tick();
			return;
		}
		this.fallDistance = 0;
		boolean isAboveBlock = world.getHeight(Heightmap.Type.MOTION_BLOCKING,this.getPosition()).getY() >= 4;
		if (
				(navigator.getPath()==null || navigator.getPath().isFinished()) && ((((shouldRepathfind() && this.onGround)||(this.getLastDamageSource()!=null&&this.getLastDamageSource().getTrueSource()!=null))) || !isAboveBlock)
		) {
			navigator.clearPath();
			navigator.resetRangeMultiplier();
			navigator.setPath(getPath(), 1);
		}
		this.setOnGround(navigator.noPath());
		if (navigator.getPath() != null) {
			Vector3d vector3d2 = new Vector3d(
					navigator.getPath().getTarget().getX()+0.5,
					navigator.getPath().getTarget().getY(),
					navigator.getPath().getTarget().getZ()+0.5
			).subtract(this.getPositionVec());
			Vector3d vector3d3 = vector3d2.mul(1,0,1);
			if (
					vector3d3.squareDistanceTo(new Vector3d(0,0,0)) <=
					0.5f
			) {
				this.onGround = true;
				this.setMotion(this.getMotion().x, Math.max(-0.1, this.getMotion().getY()-0.1f), this.getMotion().z);
				if (!this.world.getBlockState(this.getPosition().down()).isAir()) navigator.clearPath();
			} else {
				this.onGround = false;
				boolean isAbove =
						(this.getPositionVec().y >=
								(
										this.navigator.getPath().getTarget().getY()
												+ vector3d3.squareDistanceTo(new Vector3d(0, 0, 0))/32f
								));
				this.setMotion(
						MathHelper.lerp(0.1f, this.getMotion().x, vector3d2.normalize().x),
						isAbove ? Math.max(-0.1,this.getMotion().y-0.05f) : MathHelper.lerp(0.2f, Math.max(-0.1, this.getMotion().getY()), 0.5f),
						MathHelper.lerp(0.1f, this.getMotion().z, vector3d2.normalize().z)
				);
			}
		}
		if (FMLEnvironment.dist.isClient() && !FMLEnvironment.production) {
			if (this.navigator.getPath() != null) {
				Minecraft.getInstance().debugRenderer.pathfinding.addPath(this.getEntityId(),navigator.getPath(), 0.5f);
			}
		}
		if (this.getPosY() <= 3)
			this.setMotion(this.getMotion().x,MathHelper.lerp(0.1f,this.getMotion().y,-0.05f),this.getMotion().z);
		if (this.getPosY() <= 2.9)
			this.setMotion(this.getMotion().x,MathHelper.lerp(0.1f,this.getMotion().y,-0.01),this.getMotion().z);
		if (this.getPosY() <= 2.8)
			this.setMotion(this.getMotion().x,(this.getMotion().y+0.5f),this.getMotion().z);
		super.tick();
	}
	
	protected float limitAngle(float sourceAngle, float targetAngle, float maximumChange) {
		float f = MathHelper.wrapDegrees(targetAngle - sourceAngle);
		if (f > maximumChange) {
			f = maximumChange;
		}
		
		if (f < -maximumChange) {
			f = -maximumChange;
		}
		
		float f1 = sourceAngle + f;
		if (f1 < 0.0F) {
			f1 += 360.0F;
		} else if (f1 > 360.0F) {
			f1 -= 360.0F;
		}
		
		return f1;
	}
}
