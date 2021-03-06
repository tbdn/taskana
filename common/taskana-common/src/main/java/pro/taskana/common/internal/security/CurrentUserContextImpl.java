package pro.taskana.common.internal.security;

import static pro.taskana.common.internal.util.CheckedFunction.wrap;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Principal;
import java.security.acl.Group;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.security.auth.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pro.taskana.common.api.security.CurrentUserContext;

public class CurrentUserContextImpl implements CurrentUserContext {

  private static final String GET_UNIQUE_SECURITY_NAME_METHOD = "getUniqueSecurityName";
  private static final String GET_CALLER_SUBJECT_METHOD = "getCallerSubject";
  private static final String WSSUBJECT_CLASSNAME = "com.ibm.websphere.security.auth.WSSubject";

  private static final Logger LOGGER = LoggerFactory.getLogger(CurrentUserContext.class);
  private final boolean shouldUseLowerCaseForAccessIds;
  private boolean runningOnWebSphere;

  public CurrentUserContextImpl(boolean shouldUseLowerCaseForAccessIds) {
    this.shouldUseLowerCaseForAccessIds = shouldUseLowerCaseForAccessIds;
    try {
      Class.forName(WSSUBJECT_CLASSNAME);
      LOGGER.debug("WSSubject detected. Assuming that Taskana runs on IBM WebSphere.");
      runningOnWebSphere = true;
    } catch (ClassNotFoundException e) {
      LOGGER.debug("No WSSubject detected. Using JAAS subject further on.");
      runningOnWebSphere = false;
    }
  }

  @Override
  public String getUserid() {
    return runningOnWebSphere ? getUserIdFromWsSubject() : getUserIdFromJaasSubject();
  }

  @Override
  public List<String> getGroupIds() {
    Subject subject = Subject.getSubject(AccessController.getContext());
    LOGGER.trace("Subject of caller: {}", subject);
    if (subject != null) {
      Set<Group> groups = subject.getPrincipals(Group.class);
      LOGGER.trace("Public groups of caller: {}", groups);
      return groups.stream()
          .map(Principal::getName)
          .filter(Objects::nonNull)
          .map(this::convertAccessId)
          .collect(Collectors.toList());
    }
    LOGGER.trace("No groupIds found in subject!");
    return Collections.emptyList();
  }

  @Override
  public List<String> getAccessIds() {
    List<String> accessIds = new ArrayList<>(getGroupIds());
    accessIds.add(getUserid());
    return accessIds;
  }

  /**
   * Returns the unique security name of the first public credentials found in the WSSubject as
   * userid.
   *
   * @return the userid of the caller. If the userid could not be obtained, null is returned.
   */
  private String getUserIdFromWsSubject() {
    try {
      Class<?> wsSubjectClass = Class.forName(WSSUBJECT_CLASSNAME);
      Method getCallerSubjectMethod =
          wsSubjectClass.getMethod(GET_CALLER_SUBJECT_METHOD, (Class<?>[]) null);
      Subject callerSubject = (Subject) getCallerSubjectMethod.invoke(null, (Object[]) null);
      LOGGER.debug("Subject of caller: {}", callerSubject);
      if (callerSubject != null) {
        Set<Object> publicCredentials = callerSubject.getPublicCredentials();
        LOGGER.debug("Public credentials of caller: {}", publicCredentials);
        return publicCredentials.stream()
            .map(
                wrap(
                    credential ->
                        credential
                            .getClass()
                            .getMethod(GET_UNIQUE_SECURITY_NAME_METHOD, (Class<?>[]) null)
                            .invoke(credential, (Object[]) null)))
            .peek(
                o ->
                    LOGGER.debug(
                        "Returning the unique security name of first public credential: {}", o))
            .map(Object::toString)
            .map(this::convertAccessId)
            .findFirst()
            .orElse(null);
      }
    } catch (Exception e) {
      LOGGER.warn("Could not get user from WSSubject. Going ahead unauthorized.");
    }
    return null;
  }

  private String getUserIdFromJaasSubject() {
    Subject subject = Subject.getSubject(AccessController.getContext());
    LOGGER.trace("Subject of caller: {}", subject);
    if (subject != null) {
      Set<Principal> principals = subject.getPrincipals();
      LOGGER.trace("Public principals of caller: {}", principals);
      return principals.stream()
          .filter(principal -> !(principal instanceof Group))
          .map(Principal::getName)
          .filter(Objects::nonNull)
          .map(this::convertAccessId)
          .findFirst()
          .orElse(null);
    }
    LOGGER.trace("No userId found in subject!");
    return null;
  }

  private String convertAccessId(String accessId) {
    String toReturn = accessId;
    if (shouldUseLowerCaseForAccessIds) {
      toReturn = accessId.toLowerCase();
    }
    LOGGER.trace("Found AccessId '{}'. Returning AccessId '{}' ", accessId, toReturn);
    return toReturn;
  }
}
