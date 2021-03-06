package net.canadensys.harvester.occurrence.reader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.canadensys.dataportal.occurrence.model.OccurrenceRawModel;
import net.canadensys.harvester.ItemMapperIF;
import net.canadensys.harvester.ItemReaderIF;
import net.canadensys.harvester.occurrence.SharedParameterEnum;
import net.canadensys.harvester.occurrence.mapper.OccurrenceMapper;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.io.UnsupportedArchiveException;

import com.google.common.collect.Lists;

/**
 * Item reader for Darwin Core Archive.
 * This class is mutable.
 *
 * @author canadensys
 *
 */
public class DwcaItemReader extends AbstractDwcaReaderSupport implements ItemReaderIF<OccurrenceRawModel> {
	// get log4j handler
	private static final Logger LOGGER = Logger.getLogger(DwcaItemReader.class);

	private final AtomicBoolean canceled = new AtomicBoolean(false);
	private final ItemMapperIF<OccurrenceRawModel> mapper = new OccurrenceMapper();
	private final List<String> dwcaIdExcludeList = Lists.newArrayList();

	@Override
	public OccurrenceRawModel read() {

		OccurrenceRawModel occurrenceRawModel;
		do {
			if (canceled.get() || !rowsIt.hasNext()) {
				return null;
			}

			// ImmutableMap from Google Collections?
			Map<String, Object> properties = new HashMap<String, Object>();
			int i = 0;
			String[] data = rowsIt.next();
			for (String currHeader : headers) {
				properties.put(currHeader, data[i]);
				i++;
			}
			// check if some default values must be handled
			if (defaultValues != null) {
				for (String defaultValueCol : defaultValues.keySet()) {
					properties.put(defaultValueCol, defaultValues.get(defaultValueCol));
				}
			}
			occurrenceRawModel = mapper.mapElement(properties);
		}
		while (shouldSkipRecord(occurrenceRawModel));

		return occurrenceRawModel;
	}

	/**
	 * Responsible to set DWCA_USED_TERMS
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void openReader(Map<SharedParameterEnum, Object> sharedParameters) {
		dwcaFilePath = (String) sharedParameters.get(SharedParameterEnum.DWCA_PATH);
		if (mapper == null) {
			throw new IllegalStateException("No mapper defined");
		}
		if (StringUtils.isBlank(dwcaFilePath)) {
			throw new IllegalStateException("sharedParameters missing: DWCA_PATH is required.");
		}

		// handle exclusion list if provided
		if (sharedParameters.containsKey(SharedParameterEnum.DWCA_ID_EXCLUSION_LIST)) {
			dwcaIdExcludeList.addAll((List<String>) sharedParameters.get(SharedParameterEnum.DWCA_ID_EXCLUSION_LIST));
		}

		File dwcaFile = new File(dwcaFilePath);
		Archive dwcArchive;
		try {
			dwcArchive = ArchiveFactory.openArchive(dwcaFile);
			prepareReader(dwcArchive.getCore());
		}
		catch (UnsupportedArchiveException e) {
			LOGGER.fatal("Can't open DwcaItemReader", e);
		}
		catch (IOException e) {
			LOGGER.fatal("Can't open DwcaItemReader", e);
		}

		// only use terms we know
		List<String> usedDwcTerms = getDwcaUsedTerms();

		// set the used dwc terms used by this archive
		sharedParameters.put(SharedParameterEnum.DWCA_USED_TERMS, usedDwcTerms);
	}

	/**
	 * Method used to discard(skip) some records allowing to partially import a resource.
	 * This option should be used carefully in exceptional circumstance when an archive needs to be harvested
	 * and faulty records (no or duplicated coredId) can not be fixed.
	 *
	 * @param occurrenceRawModel
	 * @return
	 */
	private boolean shouldSkipRecord(OccurrenceRawModel occurrenceRawModel) {
		if (!dwcaIdExcludeList.isEmpty()) {
			return dwcaIdExcludeList.contains(occurrenceRawModel.getDwcaid());
		}
		return false;
	}

	/**
	 * Get headers found in the archive that can be mapped to OccurrenceRawModel.
	 *
	 * @return list of terms (simpleName)
	 */
	private List<String> getDwcaUsedTerms() {
		OccurrenceRawModel testModel = new OccurrenceRawModel();
		List<String> usedDwcTerms = new ArrayList<String>();

		for (String currHeader : headers) {
			if (PropertyUtils.isWriteable(testModel, currHeader)) {
				usedDwcTerms.add(currHeader);
			}
			else {
				LOGGER.warn("Property [" + currHeader + "] is not found or writeable in OccurrenceRawModel");
			}
		}

		if (defaultValues != null) {
			for (String currHeader : defaultValues.keySet()) {
				if (PropertyUtils.isWriteable(testModel, currHeader)) {
					usedDwcTerms.add(currHeader);
				}
				else {
					LOGGER.warn("Property [" + currHeader + "] is not found or writeable in OccurrenceRawModel");
				}
			}
		}
		return usedDwcTerms;
	}

	@Override
	public void closeReader() {
		super.closeReader();
	}

	@Override
	public void abort() {
		canceled.set(true);
	}
}
