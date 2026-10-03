package crazylimits.dragonsworn.mc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.breath.client.BreathRender;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

import java.util.Map;

/**
 * Draws the vanilla Ender Dragon with the Dragonsworn model, turned and banked by the procedural body
 * (see {@link #applyRotations}). The crystal healing beam and the death rays are kept.
 *
 * <p>GeckoLib 5 draws from a render state: {@link State} carries the dragon itself (the procedural layer
 * reads its brain), and the bones are posed in {@link #adjustModelBonesForRender}, where GeckoLib hands
 * over the pass's snapshots once the animation has set them.
 */
public final class DragonRenderer extends GeoReplacedEntityRenderer<ReplacedEnderDragon, EnderDragon, DragonRenderer.State> {
	/** The model reaches ~12 blocks from the entity's origin, well past the vanilla culling box. */
	private static final double CULL_MARGIN = 14.0;
	private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);

	/**
	 * What one frame draws of a dragon. GeckoLib injects {@link GeoRenderState} into every entity render state
	 * (Fabric sees that at compile time, NeoForge does not): it is implemented here outright, the same way.
	 */
	public static final class State extends EntityRenderState implements GeoRenderState {
		private final Map<DataTicket<?>, Object> geckolibData = new Reference2ObjectOpenHashMap<>();
		EnderDragon dragon;
		float partialTick;
		/** The body's lift, yaw, pitch and roll (blocks, degrees), as {@link DragonBody} gives them. */
		double lift, yaw, pitch, roll;
		/** Ticks into vanilla's dying (0: alive), and the crystal beam's offset from the dragon (null: none). */
		float deathTime;
		Vec3 beamOffset;
		/** The heat glow ({@link HeatGlowLayer}): how bright, and how far it has climbed. */
		double heatBrightness, heat;

		// GeckoLib's own injection writes to a map of its own: every access goes to this one instead
		@Override
		public <D> void addGeckolibData(DataTicket<D> ticket, D data) {
			geckolibData.put(ticket, data);
		}

		@Override
		public boolean hasGeckolibData(DataTicket<?> ticket) {
			return geckolibData.containsKey(ticket);
		}

		@Override
		public Map<DataTicket<?>, Object> getDataMap() {
			return geckolibData;
		}
	}

	private final DragonModel dragonModel;

	public DragonRenderer(EntityRendererProvider.Context context) {
		this(context, new DragonModel());
	}

	private DragonRenderer(EntityRendererProvider.Context context, DragonModel model) {
		super(context, model, new ReplacedEnderDragon());
		this.dragonModel = model;
		withRenderLayer(new AutoGlowingGeoLayer<>(this));
		withRenderLayer(new HeatGlowLayer(this));
		this.shadowRadius = 0;
	}

	@Override
	public State createRenderState(ReplacedEnderDragon animatable, EnderDragon dragon) {
		return new State();
	}

	@Override
	public void addRenderData(ReplacedEnderDragon animatable, EnderDragon dragon, State state, float partialTick) {
		state.addGeckolibData(ReplacedEnderDragon.DRAGON, dragon);
		state.dragon = dragon;
		state.partialTick = partialTick;
		DragonBody body = DragonswornDragon.brain(dragon).body;
		state.lift = body.lift(partialTick);
		state.yaw = body.yaw(partialTick);
		state.pitch = body.pitch(partialTick);
		state.roll = body.roll(partialTick);
		state.deathTime = dragon.dragonDeathTime > 0 ? dragon.dragonDeathTime + partialTick : 0.0F;
		EndCrystal crystal = dragon.nearestCrystal;
		state.beamOffset = crystal == null ? null
				: crystal.getPosition(partialTick).add(0.0, EndCrystalRenderer.getY(crystal.time + partialTick), 0.0).subtract(dragon.getPosition(partialTick));
	}

	/**
	 * The body's yaw, pitch and bank from the procedural layer ({@link DragonBody}): the body follows the
	 * steering a few ticks late (the head leads), rolls into turns and pitches with the climb. Pitch and
	 * roll turn the model about the middle of the torso. {@code PartSolver} places the hitboxes the
	 * same way.
	 */
	@Override
	protected void applyRotations(RenderPassInfo<State> renderPassInfo, PoseStack poseStack, float nativeScale) {
		State state = renderPassInfo.renderState();
		poseStack.translate(0.0, state.lift, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees((float) -state.yaw));
		poseStack.translate(0.0, BodyFrame.CENTER_Y, BodyFrame.CENTER_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees((float) state.pitch));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) -state.roll));
		poseStack.translate(0.0, -BodyFrame.CENTER_Y, -BodyFrame.CENTER_Z);
	}

	/** The procedural layer over the animation ({@link DragonModel#pose}), then what reads the drawn model. */
	@Override
	public void adjustModelBonesForRender(RenderPassInfo<State> renderPassInfo, BoneSnapshots snapshots) {
		State state = renderPassInfo.renderState();
		if (state.dragon == null) return;
		GeoBones.Model model = new GeoBones.Model(renderPassInfo.model(), snapshots);
		dragonModel.pose(model, state.dragon, state.partialTick);
		BreathRender.afterRender(state.dragon, model, state.partialTick);
		LimbContact.afterRender(state.dragon, model, state.partialTick);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (state.deathTime > 0.0F) {
			float progress = state.deathTime / 200.0F;
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees((float) -state.yaw));
			poseStack.translate(0.0F, 3.5F, -1.0F);
			submitRays(poseStack, progress, collector, RenderTypes.dragonRays());
			submitRays(poseStack, progress, collector, RenderTypes.dragonRaysDepth());
			poseStack.popPose();
		}
		if (state.beamOffset != null) {
			EnderDragonRenderer.submitCrystalBeams((float) state.beamOffset.x, (float) state.beamOffset.y, (float) state.beamOffset.z,
					state.ageInTicks, poseStack, collector, state.lightCoords);
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(EnderDragon dragon) {
		return super.getBoundingBoxForCulling(dragon).inflate(CULL_MARGIN);
	}

	/** The vanilla death rays: light bursting out of the dragon while it dies. */
	private static void submitRays(PoseStack poseStack, float progress, SubmitNodeCollector collector, RenderType type) {
		collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
			float fade = Math.min(progress > 0.8F ? (progress - 0.8F) / 0.2F : 0.0F, 1.0F);
			int center = ARGB.colorFromFloat(1.0F - fade, 1.0F, 1.0F, 1.0F);
			int edge = 0xFF00FF;
			RandomSource random = RandomSource.create(432L);
			Vector3f origin = new Vector3f(), a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
			Quaternionf rotation = new Quaternionf();
			int count = Mth.floor((progress + progress * progress) / 2.0F * 60.0F);
			for (int i = 0; i < count; i++) {
				rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI)
						.rotateXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
								random.nextFloat() * Mth.TWO_PI + progress * Mth.HALF_PI);
				pose.rotate(rotation);
				float length = random.nextFloat() * 20.0F + 5.0F + fade * 10.0F;
				float width = random.nextFloat() * 2.0F + 1.0F + fade * 2.0F;
				a.set(-HALF_SQRT_3 * width, length, -0.5F * width);
				b.set(HALF_SQRT_3 * width, length, -0.5F * width);
				c.set(0.0F, length, width);
				consumer.addVertex(pose, origin).setColor(center);
				consumer.addVertex(pose, a).setColor(edge);
				consumer.addVertex(pose, b).setColor(edge);
				consumer.addVertex(pose, origin).setColor(center);
				consumer.addVertex(pose, b).setColor(edge);
				consumer.addVertex(pose, c).setColor(edge);
				consumer.addVertex(pose, origin).setColor(center);
				consumer.addVertex(pose, c).setColor(edge);
				consumer.addVertex(pose, a).setColor(edge);
			}
		});
	}
}
