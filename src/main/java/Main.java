import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.dstu3.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;


public class Main {
    static Logger LOGGER;
    final static String LOG_FILE_PATH = "src/main/resources/log.txt";
    final static String MAIN_CLASS = "Main.class";
    private static FhirContext fhirContext;
    private static IGenericClient client;

    public static void main(String[] args){
        LOGGER = Logger.getLogger("FHIR-LOGGER");
        fhirContext = initFhirContext();
        String serverBaseUrl = "https://hapi.fhir.org/baseDstu3";
        client = fhirContext.newRestfulGenericClient(serverBaseUrl);
        handleArgs(args);
    }

    private static void searchPatientResource(String name) {
        try {
            Bundle response = client.search()
                    .forResource(Patient.class)
                    .where(Patient.NAME.matches().values(name))
                    .returnBundle(Bundle.class)
                    .execute();

            System.out.println("Found " + response.getEntry().size() + " patients. Their logical IDs are:");
            response.getEntry().forEach((entry) -> {
                // within each entry is a resource - print its logical ID
                System.out.println(entry.getResource().getIdElement().getIdPart());
            });
        } catch (Exception e) {
            System.out.println("An error occurred trying to search:");
            e.printStackTrace();
        }
    }

    private static void handleArgs(String[] args) {
        if (args.length < 1) {
            LOGGER.warning("Please add a command-line argument (-u for update or -s for search)");
        } else if (args[0].equals("-u")) {
            uploadNewPatient();
        } else if (args[0].equals("-s")) {
            searchPatientResource("Fhirman");
        } else if (args[0].equals("-o")) {
            runOperation();
        }
    }

    private static void runOperation() {
        try {
            // Invoke $everything on our Patient
            // See http://hapifhir.io/doc_rest_client.html#Extended_Operations
            Parameters outParams = client
                    .operation()
                    .onInstance(new IdType("Patient", "2834343"))
                    .named("$everything")
                    .withNoParameters(Parameters.class) // No input parameters
                    .execute();
            Bundle result = (Bundle) outParams.getParameterFirstRep().getResource();

            System.out.println("Received " + result.getTotal()
                    + " results. The resources are:");
            result.getEntry().forEach((entry) -> {
                Resource resource = entry.getResource();
                LOGGER.info(resource.getResourceType() + "/"
                        + resource.getIdElement().getIdPart());
            });
        } catch (Exception e) {
            LOGGER.throwing(MAIN_CLASS,"An error occurred trying to operate:", e);
        }
    }

    private static void uploadNewPatient() {
        Patient patient = createPatient();
        uploadFhirResource(patient, client, fhirContext);
    }

    private static void uploadFhirResource(DomainResource resource, IGenericClient client, FhirContext fhirContext) {
        try {
            MethodOutcome outcome = client.create()
                    .resource(resource)
                    .prettyPrint()
                    .encodedXml()
                    .execute();
            IdType id = (IdType) outcome.getId();
            consoleLog(outcome, fhirContext, resource);
            log(String.format("Resource is available at: %s", id.getValue()));
        } catch (DataFormatException e) {
            LOGGER.throwing(MAIN_CLASS,"An error occurred trying to upload:", e);
        }
    }

    private static FhirContext initFhirContext() {
        FhirContext ctx = FhirContext.forDstu3();
        ctx.getRestfulClientFactory().setConnectTimeout(60 * 1000);
        ctx.getRestfulClientFactory().setSocketTimeout(60 * 1000);
        return ctx;
    }

    private static Patient createPatient() {
        Patient patient = new Patient();
        patient.addName().setUse(HumanName.NameUse.OFFICIAL)
                .addPrefix("Mr").addSuffix("Fhirman").addGiven("Sam");
        patient.addIdentifier()
                .setSystem("http://ns.electronichealth.net.au/id/hi/ihi/1.0")
                .setValue("8003608166690503");
        return patient;
    }

    private static void log(String logText) {
        try {
            File f = new File(LOG_FILE_PATH);
            f.createNewFile();
            PrintWriter out;
            if (f.exists() && !f.isDirectory()) {
                out = new PrintWriter(new FileOutputStream(f, true));
            } else {
                out = new PrintWriter(f);
            }
            out.append(logText).append("\n");
            out.close();
        } catch (SecurityException e) {
            LOGGER.throwing(MAIN_CLASS, "log(String logText)", e);
        } catch (IOException e) {
            LOGGER.throwing(MAIN_CLASS, "log(String logText)", e);
        }
    }

    private static void consoleLog(MethodOutcome outcome, FhirContext fhirContext, DomainResource resource) {
        IParser xmlParser = fhirContext.newXmlParser().setPrettyPrint(true);
        Patient receivedPatient = (Patient) outcome.getResource();
        LOGGER.info("This is what we sent up: \n"
                + xmlParser.encodeResourceToString(resource)
                + "\n\nThis is what we received: \n"
                + xmlParser.encodeResourceToString(receivedPatient));
    }
}
