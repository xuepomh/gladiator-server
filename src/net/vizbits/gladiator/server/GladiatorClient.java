package net.vizbits.gladiator.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import routing.Router;
import net.vizbits.gladiator.server.exceptions.ActionDoesNotExistException;
import net.vizbits.gladiator.server.exceptions.AlreadyConnectedException;
import net.vizbits.gladiator.server.request.BaseRequest;
import net.vizbits.gladiator.server.request.LoginRequest;
import net.vizbits.gladiator.server.response.LoginResponse;
import net.vizbits.gladiator.server.service.ClientService;
import net.vizbits.gladiator.server.utils.JsonUtils;
import net.vizbits.gladiator.server.utils.LogUtils;

public class GladiatorClient extends Thread {
  private Socket socket;
  private PrintWriter out;
  private BufferedReader in;
  private String username;
  private ClientService clientService;
  private WaitingQueue waitingQueue;
  private ClientState clientState;
  private boolean isAlive;

  public GladiatorClient(Socket socket, ClientService clientService, WaitingQueue waitingQueue)
      throws Exception {
    this.clientService = clientService;
    this.waitingQueue = waitingQueue;
    this.socket = socket;

    out = new PrintWriter(socket.getOutputStream(), true);
    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    this.start();
  }

  private boolean init() {
    LoginRequest loginRequest = JsonUtils.readFromSocket(in, LoginRequest.class);
    if (loginRequest == null || loginRequest.getUsername() == null) {
      close();
      return false;
    }
    String validationMessage;
    if ((validationMessage = validateUser(loginRequest)) != null) {
      JsonUtils.writeToSocket(out, new LoginResponse(false, validationMessage));
      close();
      return false;
    }
    LogUtils.logInfo(loginRequest.getUsername());
    this.username = loginRequest.getUsername();


    try {
      clientService.addClient(this);
    } catch (AlreadyConnectedException e) {
      LogUtils.logError(e);
      JsonUtils.writeToSocket(out, new LoginResponse(false, "Error connecting."));
      return false;
    }
    JsonUtils.writeToSocket(out, new LoginResponse(true, null));
    this.clientState = ClientState.Ready;
    return true;
  }


  @Override
  public void run() {
    if (!init())
      return;
    isAlive = true;
    while (isAlive) {
      try {
        BaseRequest baseRequest = JsonUtils.readFromSocket(in, BaseRequest.class);
        if (baseRequest == null || baseRequest.getAction() == null)
          continue;
        // do stuff
        Router.route(baseRequest.getAction(), this, baseRequest);

      } catch (ActionDoesNotExistException e) {
        break;
      }
    }
    disconnect();
  }

  private String validateUser(LoginRequest loginRequest) {
    if (loginRequest.getUsername().trim().length() == 0)
      return "Username is required";
    if (!loginRequest.getUsername().matches("^[\\w\\d]+$"))
      return "Usernames must be alphanumeric";
    if (!clientService.usernameAvailable(loginRequest.getUsername()))
      return "Username is not available";
    return null;
  }

  public void disconnect() {
    clientService.removeClient(username);
    close();

  }

  private void close() {
    try {
      if (socket != null)
        socket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String getUsername() {
    return username;
  }
}
