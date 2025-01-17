package com.infinityraider.infinitylib.modules.dynamiccamera;

import com.infinityraider.infinitylib.InfinityLib;
import com.infinityraider.infinitylib.entity.EntityTypeBase;
import com.infinityraider.infinitylib.reference.Constants;
import com.infinityraider.infinitylib.reference.Names;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * We need to extend Entity in order to be able to set it as the render view entity,
 * but we will never spawn it, therefore we do not need to register it.
 * We also only need only one instance of this entity per client
 */
@OnlyIn(Dist.CLIENT)
public class DynamicCamera extends Entity {
    private static final EntityType<DynamicCamera> TYPE =
            EntityTypeBase.entityTypeBuilder(
                    Names.Entities.CAMERA, DynamicCamera.class, SpawnFactory.getInstance(), EntityClassification.MISC,
                    EntitySize.fixed(Constants.UNIT, Constants.UNIT)
            ).build();

    private static DynamicCamera INSTANCE;

    // Tick logic gets called before the world is initialized, therefore we must wait to instantiate the camera
    @Nullable
    private static DynamicCamera getInstance() {
        if (INSTANCE == null && InfinityLib.instance.getClientWorld() != null) {
            INSTANCE = new DynamicCamera(InfinityLib.instance.getClientWorld());
        }
        return INSTANCE;
    }

    public static boolean startControllingCamera(IDynamicCameraController controller) {
        DynamicCamera camera = getInstance();
        if(camera != null) {
            return camera.startObserving(controller);
        }
        return false;
    }

    public static boolean stopControllingCamera() {
        DynamicCamera camera = getInstance();
        if(camera != null) {
            camera.stopObserving();
            return true;
        }
        return false;
    }

    public static boolean toggleCameraControl(IDynamicCameraController controller, boolean status) {
        if(isCameraActive()) {
            if(status) {
                return false;
            }
            if(getCameraController() == controller) {
                return stopControllingCamera();
            }
            return false;
        } else {
            return status && startControllingCamera(controller);
        }
    }

    @Nullable
    public static IDynamicCameraController getCameraController() {
        DynamicCamera camera = getInstance();
        if(camera != null) {
            return camera.controller;
        }
        return null;
    }

    public static Status getCameraStatus() {
        DynamicCamera camera = getInstance();
        return camera == null ? Status.IDLE : camera.getStatus();
    }

    public static int getCameraAnimationFrame() {
        DynamicCamera camera = getInstance();
        return camera == null ? 0 : camera.counter;
    }

    public static void tickCamera() {
        DynamicCamera camera = getInstance();
        if(camera != null) {
            camera.tick();
        }
    }

    // If the world is unloaded, set the instance to null and wait for a new world to be loaded to create a new instance
    public static void resetCamera() {
        INSTANCE = null;
    }

    public static boolean isCameraActive() {
        DynamicCamera camera = getInstance();
        return camera != null && camera.getStatus().isActive();
    }

    public static void onFieldOfViewUpdate(float fov) {
        DynamicCamera camera = getInstance();
        if(camera != null) {
            camera.onFieldOfViewChange(fov);
        }
    }

    public static boolean isCameraInPlayer(PlayerEntity player, float partialTick) {
        if(isCameraActive()) {
            DynamicCamera camera = getInstance();
            if(camera != null) {
                return player.getRenderBoundingBox().contains(camera.getPosition(partialTick));
            }
        }
        return false;
    }

    private Status status;

    private IDynamicCameraController controller;

    private Entity originalCamera;
    private Vector3d originalPosition;
    private Vector2f originalOrientation;
    private PointOfView originalPov;

    private int counter;

    public DynamicCamera(World world) {
        this(TYPE, world);
    }

    public DynamicCamera(EntityType<DynamicCamera> type, World world) {
        super(type, world);
        this.status = Status.IDLE;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void onFieldOfViewChange(float fov) {
        if(this.controller != null) {
            this.controller.onFieldOfViewChanged(fov);
        }
    }

    public boolean startObserving(IDynamicCameraController controller) {
        if(this.controller != null) {
            return false;
        }
        this.setStatus(Status.POSITIONING);
        this.controller = controller;
        this.originalCamera = InfinityLib.instance.proxy().getRenderViewEntity();
        Entity prevCamera = this.getOriginalRenderViewEntity();
        this.originalPosition = prevCamera.getEyePosition(1);
        this.originalOrientation = new Vector2f(prevCamera.getPitch(1), prevCamera.getYaw(1));
        this.setPositionAndRotation(
                this.originalPosition.getX(), this.originalPosition.getY(), this.originalPosition.getZ(),
                this.originalOrientation.x, this.originalOrientation.y);
        this.status = Status.POSITIONING;
        this.originalPov = Minecraft.getInstance().gameSettings.getPointOfView();
        Minecraft.getInstance().gameSettings.setPointOfView(PointOfView.FIRST_PERSON);
        InfinityLib.instance.proxy().setRenderViewEntity(this);
        return true;
    }

    public void stopObserving() {
        if(this.controller != null) {
            this.controller.onObservationEnd();
        }
        if(this.getStatus().isActive()) {
            this.setStatus(Status.RETURNING);
        }
    }

    public boolean shouldContinueObserving() {
        return this.controller.shouldContinueObserving();
    }

    public Vector3d getOriginalPosition() {
        return this.originalPosition;
    }

    public Vector2f getOriginalOrientation() {
        return this.originalOrientation;
    }

    public Vector3d getTargetPosition() {
        return this.controller.getObserverPosition();
    }

    public Vector2f getTargetOrientation() {
        return this.controller.getObserverOrientation();
    }

    public Vector3d getReturnPosition() {
        return this.getOriginalRenderViewEntity().getEyePosition(1);
    }

    public Vector2f getReturnOrientation() {
        Entity original = this.getOriginalRenderViewEntity();
        return new Vector2f(original.getPitch(1), original.getYaw(1));
    }

    protected Entity getOriginalRenderViewEntity() {
        return this.originalCamera == null ? InfinityLib.instance.getClientPlayer() : this.originalCamera;
    }

    public void setPositionAndRotation(Vector3d position, Vector2f orientation) {
        this.setPositionAndRotation(position, orientation.x, orientation.y);
    }

    public void setPositionAndRotation(Vector3d position, float pitch, float yaw) {
        this.prevPosX = this.getPosX();
        this.prevPosY = this.getPosY();
        this.prevPosZ = this.getPosZ();
        this.setRawPosition(position.getX(), position.getY(), position.getZ());
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
    }

    protected boolean moveIncrement(Vector3d startPos, Vector2f startOri, Vector3d endPos, Vector2f endOri) {
        int duration = this.getTransitionDuration();
        if (this.counter >= duration) {
            this.setPositionAndRotation(endPos, endOri);
            return true;
        } else {
            // calculate interpolation ratio (both as double and as float)
            double ratioD = (this.counter + 0.0) / duration;
            float ratioF = (this.counter + 0.0F) / duration;
            // specific yaw handling to avoid making full rotations
            float targetYaw = endOri.y % 360;
            float startYaw = startOri.y % 360;
            if(targetYaw - startYaw > 180) {
                targetYaw = targetYaw - 360;
            }
            else if(targetYaw - startYaw < -180) {
                startYaw = startYaw - 360;
            }
            // set position and angles
            this.setPositionAndRotation(
                    startPos.add(endPos.subtract(startPos).scale(ratioD)),
                    startOri.x + (endOri.x - startOri.x) * ratioF,
                    startYaw + (targetYaw - startYaw) * ratioF
            );
            return false;
        }
    }

    protected Vector3d getPosition(float partialTicks) {
        return new Vector3d(
                MathHelper.lerp(partialTicks, this.prevPosX, this.getPosX()),
                MathHelper.lerp(partialTicks, this.prevPosY, this.getPosY()),
                MathHelper.lerp(partialTicks, this.prevPosZ, this.getPosZ())
        );
    }

    protected int getTransitionDuration() {
        return this.controller.getTransitionDuration();
    }

    // Holds the camera in place, preventing jerking back and forth
    protected void holdPositionAndOrientation(Vector3d position, Vector2f orientation) {
        this.setRawPosition(position.getX(), position.getY(), position.getZ());
        this.prevPosX = this.getPosX();
        this.prevPosY = this.getPosY();
        this.prevPosZ = this.getPosZ();
        this.rotationPitch = orientation.x;
        this.prevRotationPitch = this.rotationPitch;
        this.rotationYaw = orientation.y;
        this.prevRotationYaw = this.rotationYaw;
    }

    @Override
    public void tick() {
        this.baseTick();
    }

    @Override
    public void baseTick() {
        // If analyzer or player is null, this is invalid
        if (this.controller == null) {
            if (this.status != Status.IDLE) this.reset();
            return;
        }
        // Forward tick logic to the status
        this.setStatus(this.getStatus().tick(this));
    }

    public void reset() {
        this.status = Status.IDLE;
        if (this.controller != null) {
            this.controller = null;
        }
        InfinityLib.instance.proxy().setRenderViewEntity(InfinityLib.instance.getClientPlayer());
        if (this.originalPov != null) {
            Minecraft.getInstance().gameSettings.setPointOfView(this.originalPov);
        }
        this.originalCamera = null;
        this.originalPosition = null;
        this.originalOrientation = null;
        this.originalPov = null;
    }

    @Override
    protected void registerData() {}

    @Override
    protected void readAdditional(@Nonnull CompoundNBT compound) {}

    @Override
    protected void writeAdditional(@Nonnull CompoundNBT compound) {}

    @Override
    @Nonnull
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private static final Function<DynamicCamera, Status> IDLE_TICK = (camera) -> Status.IDLE;

    private static final Function<DynamicCamera, Status> POSITIONING_TICK = (camera) -> {
        if(camera.counter <= 0) {
            camera.controller.onCameraActivated();
            camera.counter = 0;
        }
        camera.counter += 1;
        if(camera.shouldContinueObserving()) {
            if (camera.moveIncrement(
                    camera.getOriginalPosition(), camera.getOriginalOrientation(),
                    camera.getTargetPosition(), camera.getTargetOrientation())) {
                camera.controller.onObservationStart();
                return Status.OBSERVING;
            } else {
                return Status.POSITIONING;
            }
        } else {
            camera.counter = camera.getTransitionDuration() - camera.counter;
            return Status.RETURNING;
        }
    };

    private static final Function<DynamicCamera, Status> OBSERVING_TICK = (camera) -> {
        if(camera.shouldContinueObserving()) {
            if (camera.counter != 0) {
                camera.counter = 0;
            }
            camera.holdPositionAndOrientation(camera.getTargetPosition(), camera.getTargetOrientation());
            return Status.OBSERVING;
        } else {
            camera.controller.onObservationEnd();
            return Status.RETURNING;
        }
    };

    private static final Function<DynamicCamera, Status> RETURNING_TICK = (camera) -> {
        if(camera.counter <= 0) {
            camera.counter = 0;
        }
        camera.counter += 1;
        if (camera.moveIncrement(
                camera.getTargetPosition(), camera.getTargetOrientation(),
                camera.getReturnPosition(), camera.getReturnOrientation())) {
            return Status.FINISHED;
        } else {
            return Status.RETURNING;
        }
    };

    private static final Function<DynamicCamera, Status> FINISHED_TICK = (camera) -> {
        if (camera.counter != 0) {
            camera.counter = 0;
        }
        camera.controller.onCameraDeactivated();
        camera.reset();
        return Status.IDLE;
    };

    public enum Status {
        IDLE(false, IDLE_TICK),
        POSITIONING(true, POSITIONING_TICK),
        OBSERVING(true, OBSERVING_TICK),
        RETURNING(true, RETURNING_TICK),
        FINISHED(false, FINISHED_TICK);

        private final boolean active;
        private final Function<DynamicCamera, Status> tickLogic;

        Status(boolean active, Function<DynamicCamera, Status> tickLogic) {
            this.active = active;
            this.tickLogic = tickLogic;
        }

        public boolean isActive() {
            return this.active;
        }

        private Status tick(DynamicCamera camera) {
            return this.tickLogic.apply(camera);
        }
    }

    public static class SpawnFactory implements EntityType.IFactory<DynamicCamera> {
        private static final SpawnFactory INSTANCE = new SpawnFactory();

        public static SpawnFactory getInstance() {
            return INSTANCE;
        }

        private SpawnFactory() {}

        @Override
        @Nonnull
        public DynamicCamera create(@Nonnull EntityType<DynamicCamera> type, @Nonnull World world) {
            return new DynamicCamera(type, world);
        }
    }
}
