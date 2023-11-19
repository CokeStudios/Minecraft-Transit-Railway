package org.mtr.mod.resource;

import org.mtr.core.data.TransportMode;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.generated.resource.VehicleResourceSchema;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.render.RenderTrains;
import org.mtr.mod.render.StoredMatrixTransformations;

import java.util.Locale;
import java.util.function.Consumer;

public final class VehicleResource extends VehicleResourceSchema {

	public final ObjectImmutableList<Box> floors;
	public final ObjectImmutableList<Box> doorways;
	private final Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModels;
	private final Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModelsDoorsClosed;
	private final Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModelsBogie1;
	private final Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModelsBogie2;

	public VehicleResource(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		final ObjectArraySet<Box> floors = new ObjectArraySet<>();
		final ObjectArraySet<Box> doorways = new ObjectArraySet<>();
		final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModel.MaterialGroup>> materialGroupsModel = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModel.MaterialGroup>> materialGroupsModelDoorsClosed = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModel.MaterialGroup>> materialGroupsBogie1Model = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModel.MaterialGroup>> materialGroupsBogie2Model = new Object2ObjectOpenHashMap<>();
		models.forEach(vehicleModel -> vehicleModel.model.writeFloorsAndDoorways(floors, doorways, materialGroupsModel, materialGroupsModelDoorsClosed));
		models.forEach(vehicleModel -> vehicleModel.model.modelProperties.iterateParts(modelPropertiesPart -> modelPropertiesPart.mapDoors(doorways)));
		bogie1Models.forEach(vehicleModel -> vehicleModel.model.writeFloorsAndDoorways(new ObjectArraySet<>(), new ObjectArraySet<>(), new Object2ObjectOpenHashMap<>(), materialGroupsBogie1Model));
		bogie2Models.forEach(vehicleModel -> vehicleModel.model.writeFloorsAndDoorways(new ObjectArraySet<>(), new ObjectArraySet<>(), new Object2ObjectOpenHashMap<>(), materialGroupsBogie2Model));
		this.floors = new ObjectImmutableList<>(floors);
		this.doorways = new ObjectImmutableList<>(doorways);
		optimizedModels = writeToOptimizedModels(materialGroupsModel);
		optimizedModelsDoorsClosed = writeToOptimizedModels(materialGroupsModelDoorsClosed);
		optimizedModelsBogie1 = writeToOptimizedModels(materialGroupsBogie1Model);
		optimizedModelsBogie2 = writeToOptimizedModels(materialGroupsBogie2Model);
	}

	public void print() {
		Init.LOGGER.info(String.format("%s:%s", transportMode.toString().toLowerCase(Locale.ENGLISH), id));
	}

	public void queue(StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int light, ObjectArrayList<Box> openDoorways) {
		if (openDoorways.isEmpty()) {
			queue(optimizedModelsDoorsClosed, storedMatrixTransformations, vehicle, light, true);
		} else {
			queue(optimizedModels, storedMatrixTransformations, vehicle, light, false);
		}
	}

	public void queueBogie(int bogieIndex, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int light) {
		if (Utilities.isBetween(bogieIndex, 0, 1)) {
			queue(bogieIndex == 0 ? optimizedModelsBogie1 : optimizedModelsBogie2, storedMatrixTransformations, vehicle, light, true);
		}
	}

	public String getId() {
		return id;
	}

	public MutableText getName() {
		return TextHelper.translatable(name);
	}

	public int getColor() {
		return CustomResourceTools.colorStringToInt(color);
	}

	public TransportMode getTransportMode() {
		return transportMode;
	}

	public double getLength() {
		return length;
	}

	public double getWidth() {
		return width;
	}

	public double getBogie1Position() {
		return bogie1Position;
	}

	public double getBogie2Position() {
		return bogie2Position;
	}

	public double getCouplingPadding1() {
		return couplingPadding1;
	}

	public double getCouplingPadding2() {
		return couplingPadding2;
	}

	public MutableText getDescription() {
		return TextHelper.translatable(description);
	}

	public String getWikipediaArticle() {
		return wikipediaArticle;
	}

	public void iterateModels(ModelConsumer modelConsumer) {
		for (int i = 0; i < models.size(); i++) {
			final VehicleModel vehicleModel = models.get(i);
			if (vehicleModel != null) {
				modelConsumer.accept(i, vehicleModel.model);
			}
		}
	}

	public void iterateBogieModels(int bogieIndex, Consumer<DynamicVehicleModel> consumer) {
		if (Utilities.isBetween(bogieIndex, 0, 1)) {
			(bogieIndex == 0 ? bogie1Models : bogie2Models).forEach(vehicleModel -> {
				if (vehicleModel.model != null) {
					consumer.accept(vehicleModel.model);
				}
			});
		}
	}

	public boolean hasGangway1() {
		return hasGangway1;
	}

	public boolean hasGangway2() {
		return hasGangway2;
	}

	public boolean hasBarrier1() {
		return hasBarrier1;
	}

	public boolean hasBarrier2() {
		return hasBarrier2;
	}

	public static boolean matchesCondition(VehicleExtension vehicle, PartCondition partCondition, boolean noOpenDoorways) {
		switch (partCondition) {
			case AT_DEPOT:
				return !vehicle.getIsOnRoute();
			case ON_ROUTE_FORWARDS:
				return vehicle.getIsOnRoute() && !vehicle.getReversed();
			case ON_ROUTE_BACKWARDS:
				return vehicle.getIsOnRoute() && vehicle.getReversed();
			case DOORS_CLOSED:
				return vehicle.persistentVehicleData.getDoorValue() == 0 || noOpenDoorways;
			case DOORS_OPENED:
				return vehicle.persistentVehicleData.getDoorValue() > 0 && !noOpenDoorways;
			default:
				return true;
		}
	}

	private static void queue(Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModels, StoredMatrixTransformations storedMatrixTransformations, VehicleExtension vehicle, int light, boolean noOpenDoorways) {
		optimizedModels.forEach((partCondition, optimizedModel) -> {
			if (matchesCondition(vehicle, partCondition, noOpenDoorways)) {
				RenderTrains.scheduleRender(RenderTrains.QueuedRenderLayer.TEXT, (graphicsHolder, offset) -> {
					storedMatrixTransformations.transform(graphicsHolder, offset);
					CustomResourceLoader.OPTIMIZED_RENDERER.queue(optimizedModel, graphicsHolder, light);
					graphicsHolder.pop();
				});
			}
		});
	}

	private static Object2ObjectOpenHashMap<PartCondition, OptimizedModel> writeToOptimizedModels(Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModel.MaterialGroup>> materialGroupsModel) {
		final Object2ObjectOpenHashMap<PartCondition, OptimizedModel> optimizedModels = new Object2ObjectOpenHashMap<>();
		materialGroupsModel.forEach((partCondition, materialGroups) -> optimizedModels.put(partCondition, new OptimizedModel(materialGroups)));
		return optimizedModels;
	}

	@FunctionalInterface
	public interface ModelConsumer {
		void accept(int index, DynamicVehicleModel dynamicVehicleModel);
	}
}
