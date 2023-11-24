package org.mtr.mod.data;

import org.apache.commons.lang3.StringUtils;
import org.mtr.core.data.Data;
import org.mtr.core.data.Vehicle;
import org.mtr.core.integration.VehicleUpdate;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.Items;
import org.mtr.mod.client.ClientData;
import org.mtr.mod.client.VehicleRidingMovement;

import javax.annotation.Nullable;
import java.util.Locale;

public class VehicleExtension extends Vehicle implements Utilities {

	public final PersistentVehicleData persistentVehicleData;
	private static final int SHIFT_ACTIVATE_TICKS = 30;
	private static final int DISMOUNT_PROGRESS_BAR_LENGTH = 30;

	public VehicleExtension(VehicleUpdate vehicleUpdate, Data data) {
		super(vehicleUpdate.getVehicleExtraData(), null, true, new JsonReader(Utilities.getJsonObjectFromData(vehicleUpdate.getVehicle())), data);
		final PersistentVehicleData tempPersistentVehicleData = ClientData.getInstance().vehicleIdToPersistentVehicleData.get(getId());
		if (tempPersistentVehicleData == null) {
			persistentVehicleData = new PersistentVehicleData();
			ClientData.getInstance().vehicleIdToPersistentVehicleData.put(getId(), persistentVehicleData);
		} else {
			persistentVehicleData = tempPersistentVehicleData;
		}
	}

	public void updateData(@Nullable JsonObject jsonObject) {
		if (jsonObject != null) {
			updateData(new JsonReader(jsonObject.getAsJsonObject("vehicle")));
			vehicleExtraData.updateData(new JsonReader(jsonObject.getAsJsonObject("data")));
		}
	}

	public void simulate(long millisElapsed) {
		simulate(millisElapsed, null, null);
		persistentVehicleData.tick(millisElapsed, vehicleExtraData);
		final MinecraftClient minecraftClient = MinecraftClient.getInstance();
		final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();
		final String thisRouteName = vehicleExtraData.getThisRouteName();
		final String thisStationName = vehicleExtraData.getThisStationName();
		final String nextStationName = vehicleExtraData.getNextStationName();
		final String thisRouteDestination = vehicleExtraData.getThisRouteDestination();

		// Render client action bar floating text
		if (clientPlayerEntity != null && VehicleRidingMovement.getRidingVehicleCarNumberAndOffset(id) != null && showShiftProgressBar() && (!isCurrentlyManual || !isHoldingKey(clientPlayerEntity))) {
			if (speed * MILLIS_PER_SECOND > 5 || thisRouteName.isEmpty() || thisStationName.isEmpty() || thisRouteDestination.isEmpty()) {
				clientPlayerEntity.sendMessage(new Text(TextHelper.translatable("gui.mtr.vehicle_speed", Utilities.round(speed * MILLIS_PER_SECOND, 1), Utilities.round(speed * 3.6F * MILLIS_PER_SECOND, 1)).data), true);
			} else {
				final MutableText text;
				switch ((int) ((System.currentTimeMillis() / 1000) % 3)) {
					default:
						text = getStationText(thisStationName, "this");
						break;
					case 1:
						if (nextStationName.isEmpty()) {
							text = getStationText(thisStationName, "this");
						} else {
							text = getStationText(nextStationName, "next");
						}
						break;
					case 2:
						text = getStationText(thisRouteDestination, "last_" + transportMode.toString().toLowerCase(Locale.ENGLISH));
						break;
				}
				clientPlayerEntity.sendMessage(new Text(text.data), true);
			}
		}

		// TODO chat announcements (next station, route number, etc.)
	}

	public static boolean showShiftProgressBar() {
		final MinecraftClient minecraftClient = MinecraftClient.getInstance();
		final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();
		final float shiftHoldingTicks = ClientData.getShiftHoldingTicks();

		if (shiftHoldingTicks > 0 && clientPlayerEntity != null) {
			final int progressFilled = MathHelper.clamp((int) (shiftHoldingTicks * DISMOUNT_PROGRESS_BAR_LENGTH / SHIFT_ACTIVATE_TICKS), 0, DISMOUNT_PROGRESS_BAR_LENGTH);
			final String progressBar = String.format("§6%s§7%s", StringUtils.repeat('|', progressFilled), StringUtils.repeat('|', DISMOUNT_PROGRESS_BAR_LENGTH - progressFilled));
			clientPlayerEntity.sendMessage(new Text(TextHelper.translatable("gui.mtr.dismount_hold", minecraftClient.getOptionsMapped().getKeySneakMapped().getBoundKeyLocalizedText(), progressBar).data), true);
			return false;
		} else {
			return true;
		}
	}

	public static boolean isHoldingKey(@Nullable ClientPlayerEntity clientPlayerEntity) {
		return clientPlayerEntity != null && clientPlayerEntity.isHolding(Items.DRIVER_KEY.get());
	}

	private static MutableText getStationText(String text, String textKey) {
		return TextHelper.literal(text.isEmpty() ? "" : IGui.formatStationName(IGui.insertTranslation(String.format("gui.mtr.%s_station_cjk", textKey), String.format("gui.mtr.%s_station", textKey), 1, IGui.textOrUntitled(text))));
	}
}
