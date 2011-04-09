/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.portlet

import com.liferay.portal.theme.ThemeDisplay
import com.liferay.portal.model.User
import com.liferay.portal.service.ServiceContext
import java.util.Date

class MockUser extends User {
    def updateSocialParticipationEquity(p1: Double) {}
    def updateSocialContributionEquity(p1: Double) {}
    def setTimeZoneId(p1: String) {}
    def setPasswordUnencrypted(p1: String) {}
    def setPasswordModified(p1: Boolean) {}
    def setLanguageId(p1: String) {}
    def isPasswordModified = false
    def isMale = false
    def isFemale = false
    def hasReminderQuery = false
    def hasPublicLayouts = false
    def hasPrivateLayouts = false
    def hasOrganization = false
    def hasMyPlaces = false
    def hasCompanyMx(p1: String) = false
    def hasCompanyMx = false
    def getTimeZone = null
    def getUserGroups = null
    def getUserGroupIds = null
    def getTeams = null
    def getTeamIds = null
    def getSocialPersonalEquity = 0.0
    def getSocialParticipationEquity = 0.0
    def getSocialContributionEquity = 0.0
    def getRoles = null
    def getRoleIds = null
    def getReminderQueryQuestions = null
    def getPublicLayoutsPageCount = 0
    def getPrivateLayoutsPageCount = 0
    def getPasswordUnencrypted = ""
    def getPasswordPolicy = null
    def getPasswordModified = false
    def getOrganizations = null
    def getOrganizationIds = null
    def getMyPlaces(p1: Int) = null
    def getMyPlaces = null
    def getMale = false
    def getLogin = ""
    def getLocale = null
    def getGroups = null
    def getGroupIds = null
    def getGroup = null
    def getFullName = ""
    def getFemale = false
    def getDisplayURL(p1: String, p2: String) = ""
    def getDisplayURL(p1: ThemeDisplay) = ""
    def getDisplayEmailAddress = ""
    def getContact = null
    def getCompanyMx = ""
    def getBirthday = null
    def toXmlString = ""
    def compareTo(p1: User) = 0
    def setExpandoBridgeAttributes(p1: ServiceContext) {}
    def getExpandoBridge = null
    def getPrimaryKeyObj = null
    def setEscapedModel(p1: Boolean) {}
    def isEscapedModel = false
    def setCachedModel(p1: Boolean) {}
    def isCachedModel = false
    def setNew(p1: Boolean) {}
    def isNew = false
    def toEscapedModel = null
    def setActive(p1: Boolean) {}
    def isActive = false
    def getActive = false
    def setAgreedToTermsOfUse(p1: Boolean) {}
    def isAgreedToTermsOfUse = false
    def getAgreedToTermsOfUse = false
    def setLockoutDate(p1: Date) {}
    def getLockoutDate = null
    def setLockout(p1: Boolean) {}
    def isLockout = false
    def getLockout = false
    def setFailedLoginAttempts(p1: Int) {}
    def getFailedLoginAttempts = 0
    def setLastFailedLoginDate(p1: Date) {}
    def getLastFailedLoginDate = null
    def setLastLoginIP(p1: String) {}
    def getLastLoginIP = ""
    def setLastLoginDate(p1: Date) {}
    def getLastLoginDate = null
    def setLoginIP(p1: String) {}
    def getLoginIP = ""
    def setLoginDate(p1: Date) {}
    def getLoginDate = null
    def setJobTitle(p1: String) {}
    def getJobTitle = ""
    def setLastName(p1: String) {}
    def getLastName = ""
    def setMiddleName(p1: String) {}
    def getMiddleName = ""
    def setFirstName(p1: String) {}
    def getFirstName = ""
    def setComments(p1: String) {}
    def getComments = ""
    def setGreeting(p1: String) {}
    def getGreeting = ""
    def getTimeZoneId = ""
    def getLanguageId = ""
    def setPortraitId(p1: Long) {}
    def getPortraitId = 0L
    def setOpenId(p1: String) {}
    def getOpenId = ""
    def setFacebookId(p1: Long) {}
    def getFacebookId = 0L
    def setEmailAddress(p1: String) {}
    def getEmailAddress = ""
    def setScreenName(p1: String) {}
    def getScreenName = ""
    def setGraceLoginCount(p1: Int) {}
    def getGraceLoginCount = 0
    def setReminderQueryAnswer(p1: String) {}
    def getReminderQueryAnswer = ""
    def setReminderQueryQuestion(p1: String) {}
    def getReminderQueryQuestion = ""
    def setPasswordModifiedDate(p1: Date) {}
    def getPasswordModifiedDate = null
    def setPasswordReset(p1: Boolean) {}
    def isPasswordReset = false
    def getPasswordReset = false
    def setPasswordEncrypted(p1: Boolean) {}
    def isPasswordEncrypted = false
    def getPasswordEncrypted = false
    def setPassword(p1: String) {}
    def getPassword = ""
    def setContactId(p1: Long) {}
    def getContactId = 0L
    def setDefaultUser(p1: Boolean) {}
    def isDefaultUser = false
    def getDefaultUser = false
    def setModifiedDate(p1: Date) {}
    def getModifiedDate = null
    def setCreateDate(p1: Date) {}
    def getCreateDate = null
    def setCompanyId(p1: Long) {}
    def getCompanyId = 0L
    def setUserUuid(p1: String) {}
    def getUserUuid = ""
    def setUserId(p1: Long) {}
    def getUserId = 0L
    def setUuid(p1: String) {}
    def getUuid = ""
    def setPrimaryKey(p1: Long) {}
    def getPrimaryKey = 0L
}