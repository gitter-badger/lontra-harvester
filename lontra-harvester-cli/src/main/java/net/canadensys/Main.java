package net.canadensys;

import java.util.Scanner;

import net.canadensys.harvester.config.CLIProcessingConfig;
import net.canadensys.harvester.main.JobInitiatorMain;
import net.canadensys.harvester.main.MigrationMain;
import net.canadensys.harvester.main.MigrationMain.Mode;
import net.canadensys.harvester.model.CliOption;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.annotations.VisibleForTesting;

/**
 * Entry point for lontra-cli
 * 
 * @author cgendreau
 *
 */
public class Main {

	@VisibleForTesting
	protected static Class<?> CONFIG_CLASS = CLIProcessingConfig.class;

	private static Options cmdLineOptions;

	private static final String CONFIG_SHORT_OPTION = "c";
	private static final String CONFIG_OPTION = "config";

	private static final String STATUS_SHORT_OPTION = "s";
	private static final String STATUS_OPTION = "status";

	private static final String RESOURCE_LIST_SHORT_OPTION = "l";
	private static final String RESOURCE_LIST_OPTION = "list";

	// harvest related options
	private static final String HARVEST_SHORT_OPTION = "h";
	private static final String HARVEST_OPTION = "harvest";
	private static final String NO_MQ_SHORT_OPTION = "N";
	private static final String NO_MQ_OPTION = "nomq";

	private static final String EXCLUDE_SHORT_OPTION = "e";
	private static final String EXCLUDE_OPTION = "exclude";

	// migration related options
	private static final String MIGRATE_SHORT_OPTION = "m";
	private static final String MIGRATE_OPTION = "migrate";
	private static final String MIGRATE_OPTION_DRYRUN = "dryrun";
	private static final String MIGRATE_OPTION_APPLY = "apply";
	private static final String MIGRATE_OPTION_CREATE = "create";

	static {
		cmdLineOptions = new Options();
		cmdLineOptions.addOption(new Option(CONFIG_SHORT_OPTION, CONFIG_OPTION, false, "Override location of configuration file"));
		cmdLineOptions.addOption(new Option(MIGRATE_SHORT_OPTION, MIGRATE_OPTION, true, "Migrate database '" + MIGRATE_OPTION_DRYRUN + "', '"
				+ MIGRATE_OPTION_APPLY + "' or '" + MIGRATE_OPTION_CREATE + "'"));
		cmdLineOptions.addOption(new Option(RESOURCE_LIST_SHORT_OPTION, RESOURCE_LIST_OPTION, false, "List all resources"));
		cmdLineOptions.addOption(new Option(STATUS_SHORT_OPTION, STATUS_OPTION, false, "List status of resources"));
		cmdLineOptions.addOption(Option.builder(HARVEST_SHORT_OPTION).longOpt(HARVEST_OPTION)
				.desc("Harvest a specific resource or a resource that requires to be harvested if no <resource idenfifier> is provided")
				.argName("resource idenfifier").hasArg()
				.optionalArg(true)
				.build());
		cmdLineOptions.addOption(new Option(EXCLUDE_SHORT_OPTION, EXCLUDE_OPTION, true,
				"Location of an exclude file. Only used if -h is specified for a specific resource."));
		// cmdLineOptions.addOption(new Option(NO_MQ_SHORT_OPTION, NO_MQ_OPTION, false, "Harvest without using a Message Queue"));
	}

	/**
	 * Harvester CLI entry point
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		CommandLineParser parser = new DefaultParser();
		CommandLine cmdLine = null;
		try {
			cmdLine = parser.parse(cmdLineOptions, args);
		}
		catch (ParseException e) {
			System.out.println(e.getMessage());
		}

		if (cmdLine != null) {

			String configOptionValue = cmdLine.getOptionValue(CONFIG_OPTION);

			// handle migration
			if (cmdLine.hasOption(MIGRATE_OPTION)) {
				String optionValue = cmdLine.getOptionValue(MIGRATE_OPTION);
				if (MIGRATE_OPTION_DRYRUN.equalsIgnoreCase(optionValue)) {
					MigrationMain.main(Mode.DRYRUN, configOptionValue);
				}
				else if (MIGRATE_OPTION_CREATE.equalsIgnoreCase(optionValue)) {
					MigrationMain.main(Mode.CREATE, configOptionValue);
				}
				else if (MIGRATE_OPTION_APPLY.equalsIgnoreCase(optionValue)) {
					Scanner sc = new Scanner(System.in);
					// ask confirmation
					System.out.println("Are you sure you want to apply database migration? (yes/no)");

					if ("yes".equalsIgnoreCase(sc.nextLine())) {
						MigrationMain.main(Mode.MIGRATE, configOptionValue);
					}
					sc.close();
				}
				else {
					printHelp();
				}
			}
			else if (cmdLine.hasOption(RESOURCE_LIST_OPTION)) {
				JobInitiatorMain.jobMain(new CliOption(JobInitiatorMain.CommandType.LIST_RESOURCE), CONFIG_CLASS);
			}
			else if (cmdLine.hasOption(STATUS_OPTION)) {
				JobInitiatorMain.jobMain(new CliOption(JobInitiatorMain.CommandType.RESOURCE_STATUS), CONFIG_CLASS);
			}
			else if (cmdLine.hasOption(HARVEST_OPTION)) {
				String harvestOptionValue = cmdLine.getOptionValue(HARVEST_OPTION);
				String excludeOptionValue = cmdLine.getOptionValue(EXCLUDE_OPTION);
				boolean noMQ = cmdLine.hasOption(NO_MQ_OPTION);

				CliOption cliOption = new CliOption(JobInitiatorMain.CommandType.HARVEST);
				cliOption.setResourceIdentifier(harvestOptionValue);
				cliOption.setExclusionFilePath(excludeOptionValue);

				if (noMQ) {
					System.out.println("harvest " + harvestOptionValue + " with no nodes");
				}
				else {
					JobInitiatorMain.jobMain(cliOption, CONFIG_CLASS);
				}
			}
			else {
				printHelp();
			}
		}
	}

	/**
	 * Print the "usage" to the standard output.
	 */
	public static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("lontra-harvester-cli", cmdLineOptions);
	}
}
