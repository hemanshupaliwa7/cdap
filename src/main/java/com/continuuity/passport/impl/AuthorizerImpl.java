package com.continuuity.passport.impl;

import com.continuuity.passport.core.Account;
import com.continuuity.passport.core.Component;
import com.continuuity.passport.core.ComponentACL;
import com.continuuity.passport.core.Credentials;
import com.continuuity.passport.core.User;
import com.continuuity.passport.core.service.Authorizer;

/**
 *
 */
public class AuthorizerImpl implements Authorizer {


  /**
   * Authorize component for the user with the request ACLType
   * Example: Authorize User: Foo to DataSet: Bar with ACL: READ
   *
   * @param user        User requesting authorization
   * @param account     Account that owns the dataSet
   * @param component   Component for which authorization is requested
   * @param aclType     ACL requested on the component
   * @param credentials UserCredentials that authenticates the user
   * @return
   */
  @Override
  public boolean isAuthorized(User user, Account account, Component component, ComponentACL.Type aclType, Credentials
    credentials) {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
