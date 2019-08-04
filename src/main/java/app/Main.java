package app;

import cdp4common.engineeringmodeldata.EngineeringModel;
import cdp4common.engineeringmodeldata.Iteration;
import cdp4common.engineeringmodeldata.Parameter;
import cdp4common.engineeringmodeldata.PossibleFiniteState;
import cdp4common.engineeringmodeldata.PossibleFiniteStateList;
import cdp4common.sitedirectorydata.DomainOfExpertise;
import cdp4common.sitedirectorydata.EmailAddress;
import cdp4common.sitedirectorydata.ParameterType;
import cdp4common.sitedirectorydata.Person;
import cdp4common.sitedirectorydata.VcardEmailAddressKind;
import cdp4common.types.OrderedItem;
import cdp4dal.Session;
import cdp4dal.SessionImpl;
import cdp4dal.dal.Credentials;
import cdp4dal.exceptions.TransactionException;
import cdp4dal.operations.ThingTransactionImpl;
import cdp4dal.operations.TransactionContextResolver;
import cdp4servicesdal.CdpServicesDal;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicHeader;

public class Main {

  private static Session session;
  private static Credentials credentials;
  private static boolean isRunning = true;
  private static URI uri;

  // Commands
  private static final String OPEN = "open";
  private static final String REFRESH = "refresh";
  private static final String RELOAD = "reload";
  private static final String CLOSE = "close";
  private static final String RESTORE = "restore";
  private static final String GET_ITERATION = "get_iteration";
  private static final String POST_PERSON = "post_person";
  private static final String POST_PARAMETER = "post_parameter";
  private static final String POST_PFSL = "post_pfsl";
  private static final String POST_PFSL_REORDER = "post_pfsl_reorder";
  private static final String REMOVE_PARAMETER = "remove_parameter";

  public static void main(String[] args) {
    Scanner in = new Scanner(System.in);

    printSeparator();
    System.out.println("Welcome to CDP4 SDKJ sample app!");
    printSeparator();

    System.out.println("Enter your user name (default is admin, just press Enter):");
    String userName = in.nextLine().isEmpty() ? "admin" : in.nextLine();

    System.out.println("Enter your password (to use default just press Enter):");
    String pass = in.nextLine().isEmpty() ? "pass" : in.nextLine();

    System.out.println(
        "Enter a server's URL for future requests (default is https://cdp4services-test.rheagroup.com, just press Enter):");
    uri =
        URI.create(
            in.nextLine().isEmpty() ? "https://cdp4services-test.rheagroup.com" : in.nextLine());

    var dal = new CdpServicesDal();
    credentials = new Credentials(userName, pass, uri, null);
    session = new SessionImpl(dal, credentials);

    printCommands();

    while (isRunning) {
      try {
        executeCommand(in.nextLine());
      } catch (Exception ex) {
        printSeparator();
        System.out.println("Something went wrong. Sorry about that.");
        System.out.println(ex.getMessage());
        printSeparator();
      }
    }

    in.close();
  }

  private static void executeCommand(String command)
      throws TransactionException, InterruptedException, ExecutionException, URISyntaxException, IOException {
    String[] args = command.strip().split(" ");
    switch (args[0]) {
      case OPEN: {
        open();
        break;
      }
      case REFRESH: {
        refresh();
        break;
      }
      case RELOAD: {
        reload();
        break;
      }
      case GET_ITERATION: {
        getIteration();
        break;
      }
      case POST_PERSON: {
        postPerson();
        break;
      }
      case POST_PARAMETER: {
        postParameter();
        break;
      }
      case POST_PFSL: {
        postPossibleFiniteStateList();
        break;
      }
      case POST_PFSL_REORDER: {
        postPossibleFiniteStateListReorder();
        break;
      }
      case REMOVE_PARAMETER: {
        removeParameter();
        break;
      }
      case CLOSE: {
        close();
        break;
      }
      case RESTORE: {
        restore();
        break;
      }
      default: {
        System.out.println("Unrecognized command.");
      }
    }
  }

  private static void postPossibleFiniteStateListReorder() throws TransactionException {
    if (session.getOpenIterations().isEmpty()) {
      System.out.println("At first an iteration should be opened");
      return;
    }

    var iteration = session.getOpenIterations().keySet().stream().findFirst();
    if (iteration.isPresent()) {
      var iterationClone = iteration.get().clone(false);
      var pfsl = iteration.get().getPossibleFiniteStateList().stream()
          .filter(x -> x.getName().equals("PossibleFiniteStateList1"))
          .findFirst();

      if (pfsl.isEmpty()) {
        System.out.println("There is not a predefined PossibleFiniteStateList. Execute post_pfsl");
        return;
      }

      var pfslClone = pfsl.get().clone(true);

      // make sure keys are preserved
      var itemsMap = new HashMap<Object, Long>();
      pfsl.get().getPossibleState().toDtoOrderedItemList()
          .forEach(x -> itemsMap.put(x.getV(), x.getK()));
      var orderedItems = new ArrayList<OrderedItem>();
      pfslClone.getPossibleState().getSortedItems().values().forEach(x -> {
        var orderedItem = new OrderedItem();
        orderedItem.setK(itemsMap.get(x.getIid()));
        orderedItem.setV(x);
        orderedItems.add(orderedItem);
      });

      pfslClone.getPossibleState().clear();
      pfslClone.getPossibleState().addOrderedItems(orderedItems);
      pfslClone.setModifiedOn(OffsetDateTime.now());

      pfslClone.getPossibleState().move(1, 0);
      var transaction = new ThingTransactionImpl(
          TransactionContextResolver.resolveContext(iterationClone),
          iterationClone);
      transaction.createOrUpdate(pfslClone);

      session.write(transaction.finalizeTransaction()).join();

      printCacheSize();

      printCommands();
    }
  }

  private static void postPossibleFiniteStateList() throws TransactionException {
    if (session.getOpenIterations().isEmpty()) {
      System.out.println("At first an iteration should be opened");
      return;
    }

    var iteration = session.getOpenIterations().keySet().stream().findFirst();
    if (iteration.isPresent()) {
      var iterationClone = iteration.get().clone(false);
      var pfs1 = new PossibleFiniteState(UUID.randomUUID(), session.getAssembler().getCache(), uri);
      pfs1.setName("state1");
      pfs1.setShortName("s1");

      var pfs2 = new PossibleFiniteState(UUID.randomUUID(), session.getAssembler().getCache(), uri);
      pfs2.setName("state2");
      pfs2.setShortName("s2");

      var pfsList = new PossibleFiniteStateList(UUID.randomUUID(),
          session.getAssembler().getCache(), uri);
      pfsList.setName("PossibleFiniteStateList1");
      pfsList.setShortName("PFSL1");

      var domainOfExpertise = session.getOpenIterations().get(iteration.get()).getLeft();
      pfsList.setOwner(domainOfExpertise);

      var transaction = new ThingTransactionImpl(
          TransactionContextResolver.resolveContext(iterationClone),
          iterationClone);
      transaction.create(pfsList, iterationClone);
      transaction.create(pfs1, pfsList);
      transaction.create(pfs2, pfsList);

      session.write(transaction.finalizeTransaction()).join();

      printCacheSize();

      printCommands();
    }
  }

  private static void postParameter() throws TransactionException {
    if (session.getOpenIterations().isEmpty()) {
      System.out.println("At first an iteration should be opened");
      return;
    }

    var iteration = session.getOpenIterations().keySet().stream().findFirst();
    if (iteration.isPresent()) {
      var elementDefinition = iteration.get().getElement().get(0);
      var elementDefinitionClone = elementDefinition.clone(false);
      var domainOfExpertise = session.getOpenIterations().get(iteration.get()).getLeft();

      var parameter = new Parameter(UUID.randomUUID(), session.getAssembler().getCache(), uri);
      var parameterType = session.getAssembler().getCache().asMap().values().stream()
          .filter(x -> x instanceof ParameterType)
          .findFirst()
          .orElseThrow();

      parameter.setParameterType((ParameterType) parameterType);
      parameter.setOwner(domainOfExpertise);

      var transaction = new ThingTransactionImpl(
          TransactionContextResolver.resolveContext(elementDefinitionClone),
          elementDefinitionClone);
      transaction.create(parameter, elementDefinitionClone);

      session.write(transaction.finalizeTransaction()).join();

      printCacheSize();

      printCommands();
    }
  }

  private static void printSeparator() {
    System.out.println("*********************************");
  }

  private static void open() {
    session.open().join();
    printCacheSize();

    printCommands();
  }

  private static void refresh() {
    if (isSiteDirectoryUnavailable()) {
      System.out.println("At first a connection should be opened.");
      return;
    }
    session.refresh().join();
    printCacheSize();

    printCommands();
  }

  private static void reload() {
    if (isSiteDirectoryUnavailable()) {
      System.out.println("At first a connection should be opened.");
      return;
    }
    session.reload().join();
    printCacheSize();

    printCommands();
  }

  private static void close() {
    if (isSiteDirectoryUnavailable()) {
      System.out.println("At first a connection should be opened.");
      return;
    }
    try {
      session.close().join();
    } catch (Exception ex) {
      System.out.println("During close operation an error is received: ");
      System.out.println(ex.getMessage());
    }

    printSeparator();
    System.out.println("Good bye!");
    printSeparator();
    isRunning = false;
  }

  private static void restore()
      throws URISyntaxException, ExecutionException, InterruptedException, IOException {
    if (!isSiteDirectoryUnavailable()) {
      System.out.println("It is possible to restore the server only before connection is opened.");
      return;
    }

    var uriBuilder = new URIBuilder(uri);
    uriBuilder.setPath("/Data/Restore");
    HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom();

    httpClientBuilder.setDefaultHeaders(Collections.singletonList(
        new BasicHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
            .encodeToString(String.format("%s:%s", credentials.getUserName(),
                credentials.getPassword()).getBytes(StandardCharsets.US_ASCII)))
    ));

    CloseableHttpAsyncClient client = httpClientBuilder.build();
    client.start();

    var post = new HttpPost(uriBuilder.build());
    client.execute(post, null).get();
    client.close();

    printCommands();
  }

  private static void removeParameter() throws TransactionException {
    if (session.getOpenIterations().isEmpty()) {
      System.out.println("At first an iteration should be opened");
      return;
    }

    var iteration = session.getOpenIterations().keySet().stream().findFirst();
    if (iteration.isPresent()) {
      var elementDefinition = iteration.get().getElement().get(0);
      var elementDefinitionClone = elementDefinition.clone(false);
      var parameterClone = elementDefinition.getParameter().get(0).clone(false);

      var transaction = new ThingTransactionImpl(
          TransactionContextResolver.resolveContext(elementDefinitionClone),
          elementDefinitionClone);
      transaction.delete(parameterClone, elementDefinitionClone);

      session.write(transaction.finalizeTransaction()).join();

      printCacheSize();

      printCommands();
    }
  }

  private static void getIteration() {
    var siteDirectory = session.getAssembler().retrieveSiteDirectory();
    if (isSiteDirectoryUnavailable()) {
      System.out.println("At first a connection should be opened.");
      return;
    }

    var engineeringModelIid = siteDirectory.getModel().get(0).getEngineeringModelIid();
    var iterationIid = siteDirectory.getModel().get(0).getIterationSetup().get(0).getIterationIid();
    var domainOfExpertiseIid = siteDirectory.getModel().get(0).getActiveDomain().get(0).getIid();

    var model = new EngineeringModel(engineeringModelIid, session.getAssembler().getCache(), uri);
    var iteration = new Iteration(iterationIid, session.getAssembler().getCache(), uri);
    iteration.setContainer(model);
    var domainOfExpertise = new DomainOfExpertise(domainOfExpertiseIid,
        session.getAssembler().getCache(), uri);

    session.read(iteration, domainOfExpertise).join();

    printCacheSize();

    printCommands();
  }

  private static void postPerson() throws TransactionException {
    if (isSiteDirectoryUnavailable()) {
      System.out.println("At first a connection should be opened.");
      return;
    }

    // Create person object
    var person = new Person(UUID.randomUUID(), session.getAssembler().getCache(), uri);
    person.setActive(true);
    person.setShortName("M" + LocalDateTime.now().toString());
    person.setSurname("Mouse");

    var email1 = new EmailAddress(UUID.randomUUID(), session.getAssembler().getCache(), uri);
    email1.setValue("mikki.home@mouse.com");
    email1.setVcardType(VcardEmailAddressKind.HOME);

    person.setDefaultEmailAddress(email1);
    person.setGivenName("Mike");
    person.setPassword("password");

    var email2 = new EmailAddress(UUID.randomUUID(), session.getAssembler().getCache(), uri);
    email2.setValue("mikki.work@mouse.com");
    email2.setVcardType(VcardEmailAddressKind.WORK);

    var modifiedSiteDirectory = session.getAssembler().retrieveSiteDirectory().clone(true);

    var transaction = new ThingTransactionImpl(
        TransactionContextResolver.resolveContext(modifiedSiteDirectory), modifiedSiteDirectory);
    transaction.create(person, modifiedSiteDirectory);
    transaction.create(email1, person);
    transaction.create(email2, person);

    session.write(transaction.finalizeTransaction()).join();

    printCacheSize();

    printCommands();
  }

  private static void printCacheSize() {
    System.out
        .println(session.getAssembler().getCache().size() + " objects currently in the cache.");
  }

  private static void printCommands() {
    printSeparator();
    System.out.println("Available commands:");
    System.out.println(OPEN + " - Open a connection to a data-source");
    System.out.println(REFRESH + " - Update the Cache with updated Things from a data-source");
    System.out
        .println(RELOAD + " - Reload all Things from a data-source for all open TopContainers");
    System.out.println(
        CLOSE
            + " - Close the connection to a data-source and clear the Cache and exits the program");
    System.out
        .println(RESTORE + " - Restores the state of a data-source to its default state");
    System.out.println(
        GET_ITERATION
            + " - gets a predefined iteration of an engineering model with dependent objects");
    System.out.println(POST_PERSON + " - posts a predefined person with 2 e-mail addresses");
    System.out.println(POST_PARAMETER + " - posts a predefined parameter");
    System.out.println(
        POST_PFSL + " - posts a predefined PossibleFiniteStateList with 2 PossibleFiniteStates");
    System.out.println(
        POST_PFSL_REORDER
            + " - reorders(rotates in this particular case) states in the created predefined PossibleFiniteStateList (post_pfsl)");
    System.out.println(REMOVE_PARAMETER + " - removes a predefined Parameter of ElementDefinition");
    printSeparator();
  }

  private static boolean isSiteDirectoryUnavailable() {
    return session.getAssembler().retrieveSiteDirectory() == null;
  }
}
