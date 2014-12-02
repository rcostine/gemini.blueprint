/******************************************************************************
 * Copyright (c) 2006, 2010 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * The Eclipse Public License is available at 
 * http://www.eclipse.org/legal/epl-v10.html and the Apache License v2.0
 * is available at http://www.opensource.org/licenses/apache2.0.php.
 * You may elect to redistribute this code under either of these licenses. 
 *
 * Contributors:
 *   VMware Inc.
 *****************************************************************************/

package org.eclipse.gemini.blueprint.iandt.bundleScope;

import java.io.FilePermission;
import java.lang.reflect.ReflectPermission;
import java.security.AllPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.PropertyPermission;

import org.eclipse.gemini.blueprint.context.ConfigurableOsgiBundleApplicationContext;
import org.eclipse.gemini.blueprint.iandt.scope.common.ScopeTestService;
import org.eclipse.gemini.blueprint.test.AbstractBlueprintTest;
import org.eclipse.gemini.blueprint.util.OsgiFilterUtils;
import org.eclipse.gemini.blueprint.util.OsgiServiceReferenceUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;
import org.osgi.framework.*;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.ObjectUtils;

import static org.eclipse.gemini.blueprint.test.BlueprintOptions.blueprintDefaults;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

/**
 * Integration tests for 'bundle' scoped beans.
 *
 * @author Costin Leau
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
@ContextConfiguration(locations = {"classpath:org/eclipse/gemini/blueprint/iandt/bundleScope/scope-context.xml"})
public class ScopingTest extends AbstractBlueprintTest {

//    protected String getManifestLocation() {
//        return "org/eclipse/gemini/blueprint/iandt/bundleScope/ScopingTest.MF";
//    }

    @Configuration
    public Option[] config() {
        return options(blueprintDefaults(),
                mavenBundle("org.eclipse.gemini.blueprint.iandt", "scoped.bundle.common").versionAsInProject(),
                mavenBundle("org.eclipse.gemini.blueprint.iandt", "scoped.bundle.a").versionAsInProject(),
                mavenBundle("org.eclipse.gemini.blueprint.iandt", "scoped.bundle.b").versionAsInProject());
    }

    @Before
    public void beforeEach() {
        PermissionAdmin pa;
        ServiceReference<PermissionAdmin> ref = bundleContext.getServiceReference(PermissionAdmin.class);

        if (ref != null) {
            logger.trace("Found permission admin " + ref);
            pa = bundleContext.getService(ref);
//            bc.addBundleListener(this);
            logger.trace("Default permissions are " + ObjectUtils.nullSafeToString(pa.getDefaultPermissions()));
            logger.warn("Security turned ON");

            pa.setPermissions(bundleContext.getBundle().getLocation(), getPIFromPermissions(getTestPermissions()));
        }

    }

    private PermissionInfo[] getPIFromPermissions(List<Permission> perms) {
        PermissionInfo[] pi = new PermissionInfo[perms.size()];
        int index = 0;
        for (Permission perm : perms) {
            pi[index++] = new PermissionInfo(perm.getClass().getName(), perm.getName(), perm.getActions());
        }
        return pi;
    }

    @Test
    //@Ignore("Failing for some reason")
    public void testEnvironmentValidity() throws Exception {
        assertNotNull(getServiceA());
        assertNotNull(getServiceB());
    }

    @Test
    public void testServiceAScopeForCurrentBundle() throws Exception {
        ScopeTestService serviceAcopy1 = getServiceA();
        ScopeTestService serviceAcopy2 = getServiceA();

        assertEquals("different bean instances given for the same bundle", serviceAcopy1, serviceAcopy2);
    }

    @Test
    public void testServiceAScopeForBundleA() throws Exception {
        ScopeTestService serviceAInBundleA = (ScopeTestService) org.eclipse.gemini.blueprint.iandt.scope.a.BeanReference.BEAN;

        System.out.println(serviceAInBundleA.getServiceIdentity());
        System.out.println(getServiceA().getServiceIdentity());

        assertFalse("same bean instance used for different bundles", serviceAInBundleA.getServiceIdentity().equals(
                getServiceA().getServiceIdentity()));
    }

    @Test
    //@Ignore("Failing for some reason")
    public void testServiceAScopeForBundleB() throws Exception {
        String symName = "org.eclipse.gemini.blueprint.iandt.scope.b";
        ScopeTestService serviceAInBundleB = (ScopeTestService) getAppCtx(symName).getBean("serviceFromA");

        assertFalse("same bean instance used for different bundles", serviceAInBundleB.getServiceIdentity().equals(
                getServiceA().getServiceIdentity()));
    }

    @Test
    //@Ignore("Failing for some reason")
    public void testServiceBInBundleBAndTestBundle() throws Exception {
        ScopeTestService serviceAInBundleB = (ScopeTestService) org.eclipse.gemini.blueprint.iandt.scope.b.BeanReference.BEAN;

        assertFalse("same bean instance used for different bundles", serviceAInBundleB.getServiceIdentity().equals(
                getServiceB().getServiceIdentity()));
    }

    @Test
    public void testScopedBeanNotExported() throws Exception {
        Properties props = (Properties) applicationContext.getBean("props");
        // ask for it again
        Properties another = (Properties) applicationContext.getBean("props");
        assertSame("different instances returned for the same scope", props, another);
    }

    @Test
    public void testBeanReferenceAndLocalScopeInstanceForBundleA() throws Exception {
        String symName = "org.eclipse.gemini.blueprint.iandt.scope.a";
        assertSame("local references are different", getAppCtx(symName).getBean("a.service"),
                org.eclipse.gemini.blueprint.iandt.scope.a.BeanReference.BEAN);
    }

    @Test
    //@Ignore("Failing for some reason")
    public void testBeanReferenceAndLocalScopeInstanceForBundleB() throws Exception {
        String symName = "org.eclipse.gemini.blueprint.iandt.scope.b";
        assertSame("local references are different", getAppCtx(symName).getBean("b.service"),
                org.eclipse.gemini.blueprint.iandt.scope.b.BeanReference.BEAN);
    }

    @Test
    public void testScopedBeanDestructionCallbackDuringContextRefresh() throws Exception {
        Properties props = (Properties) applicationContext.getBean("props");
        // add some content
        props.put("foo", "bar");

        // check by asking again for the bean
        Properties another = (Properties) applicationContext.getBean("props");
        assertSame(props, another);
        assertTrue(another.containsKey("foo"));

        // refresh context
        applicationContext.refresh();
        Properties refreshed = (Properties) applicationContext.getBean("props");
        assertNotSame("context refresh does not clean scoped objects", props, refreshed);
        assertTrue(refreshed.isEmpty());
        // check that props object has been cleaned also
        assertTrue("destroy callback wasn't called/applied", props.isEmpty());
    }

    @Test
    public void testExportedScopedBeansDestructionCallbackCalled() throws Exception {


        Object rawServiceA = getServiceA();
        assertTrue(rawServiceA instanceof Properties);
        Properties props = (Properties) rawServiceA;
        // modify properties
        props.put("service", "a");

        // check service again
        assertTrue(((Properties) getServiceA()).containsKey("service"));

        // refresh opposite service
        getAppCtx("org.eclipse.gemini.blueprint.iandt.scope.a").refresh();

        // wait for the context to refresh
        Thread.sleep(1000);
        // get service a again
        assertTrue("scoped bean a did not have its callback called", ((Properties) getServiceA()).isEmpty());
    }

    protected ScopeTestService getServiceA() throws Exception {
        return getService("a");
    }

    protected ScopeTestService getServiceB() throws Exception {
        return getService("b");
    }

    protected ScopeTestService getService(String bundleName) {
        ServiceReference ref = OsgiServiceReferenceUtils.getServiceReference(bundleContext,
                ScopeTestService.class.getName(), "(Bundle-SymbolicName=org.eclipse.gemini.blueprint.iandt.scope." + bundleName
                        + ")");
        if (ref == null) {
            String filter = OsgiFilterUtils.unifyFilter(ScopeTestService.class, null);
            System.out.println(ObjectUtils.nullSafeToString(OsgiServiceReferenceUtils.getServiceReferences(
                    bundleContext, filter)));
            throw new IllegalStateException("cannot find service with owning bundle " + bundleName);
        }
        return (ScopeTestService) bundleContext.getService(ref);
    }

    private ConfigurableApplicationContext getAppCtx(String symBundle) {
        ServiceReference ref = OsgiServiceReferenceUtils.getServiceReference(bundleContext, "("
                + ConfigurableOsgiBundleApplicationContext.APPLICATION_CONTEXT_SERVICE_PROPERTY_NAME + "=" + symBundle
                + ")");

        if (ref == null)
            throw new IllegalArgumentException("cannot find appCtx for bundle " + symBundle);
        return (ConfigurableApplicationContext) bundleContext.getService(ref);
    }

    /**
     * Since the test is creating some application contexts, give it some
     * privileges.
     */
    protected List<Permission> getTestPermissions() {
        List<Permission> perms = getTestPermissionsP();
        perms.add(new AdminPermission("(name=org.eclipse.gemini.blueprint.iandt.scope.a)", "*"));
        perms.add(new AllPermission());
        return perms;
    }

    protected List<Permission> getTestPermissionsP() {
        List<Permission> perms = new ArrayList<Permission>();
        perms.add(new PackagePermission("*", PackagePermission.EXPORT));
        perms.add(new PackagePermission("*", PackagePermission.IMPORT));
        perms.add(new BundlePermission("*", BundlePermission.HOST));
        perms.add(new BundlePermission("*", BundlePermission.PROVIDE));
        perms.add(new BundlePermission("*", BundlePermission.REQUIRE));
        perms.add(new ServicePermission("*", ServicePermission.REGISTER));
        perms.add(new ServicePermission("*", ServicePermission.GET));
        perms.add(new PropertyPermission("*", "read,write"));
        // required by Spring
        perms.add(new RuntimePermission("*", "accessDeclaredMembers"));
        perms.add(new ReflectPermission("*", "suppressAccessChecks"));
        // logging permission
        perms.add(new FilePermission("-", "write"));
        perms.add(new FilePermission("-", "read"));
        return perms;
    }

    protected List<Permission> getIAndTPermissions() {
        List<Permission> perms = new ArrayList<Permission>();
        // export package
        perms.add(new PackagePermission("*", PackagePermission.EXPORT));
        perms.add(new PackagePermission("*", PackagePermission.IMPORT));
        perms.add(new BundlePermission("*", BundlePermission.FRAGMENT));
        perms.add(new BundlePermission("*", BundlePermission.PROVIDE));
        perms.add(new ServicePermission("*", ServicePermission.REGISTER));
        perms.add(new ServicePermission("*", ServicePermission.GET));
        perms.add(new PropertyPermission("*", "read,write"));

        // required by Spring
        perms.add(new RuntimePermission("*", "accessDeclaredMembers"));
        perms.add(new ReflectPermission("*", "suppressAccessChecks"));

        // logging permission
        perms.add(new FilePermission("-", "write"));
        perms.add(new FilePermission("-", "read"));

        return perms;
    }
}