package com.rcu.dxp.core.servlets.mobile;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Objects;

import javax.servlet.Servlet;

import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcu.dxp.core.constants.CommerceConstants;
import com.rcu.dxp.core.constants.CommonConstants;
import com.rcu.dxp.core.models.ExperienceCardsCarousel;
import com.rcu.dxp.core.services.DynamicMediaService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceNotFoundException;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rcu.dxp.core.services.mobile.PageJSONServletService;
import com.rcu.dxp.core.servlets.mobile.Pojo.JSONNode;

import static com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT;
import static com.rcu.dxp.core.constants.CommonConstants.TIDY;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.TimeZone;
import static org.apache.sling.jcr.resource.api.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;

import static com.rcu.dxp.core.constants.CommonConstants.APPLICATION_JSON;
import static com.rcu.dxp.core.constants.CommonConstants.CONTENT_TYPE;
import static com.rcu.dxp.core.constants.CommonConstants.ENCODING_TYPE_UTF_8;
import static com.rcu.dxp.core.constants.CommonConstants.FORWARD_SLASH_STRING;
import static com.rcu.dxp.core.constants.CommonConstants.TITLE;
import static com.rcu.dxp.core.constants.CommonConstants.TYPE;
import static com.rcu.dxp.core.constants.CommonConstants.UNDERSCORE;

@Component(
		immediate = true,
		service = Servlet.class,
		name = "RCUDXPFetchPageJsonServlet",
		property = {
				Constants.SERVICE_DESCRIPTION + "=Page JSON renderer Servlet",
				ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
				ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + CommonConstants.RESOURCE_TYPE_CQ_PAGE,
				ServletResolverConstants.SLING_SERVLET_SELECTORS + "=" + "rcuapp",
				ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=" + CommonConstants.JSON
		}
)
public class GetPageJsonServlet extends SlingSafeMethodsServlet {

	private static final String ASSET_PATH = "assetPath";
	private static final String VIDEO = "video";
	private static final String ASSET_TYPE = "assetType";
	private static final Logger LOGGER = LoggerFactory.getLogger(GetPageJsonServlet.class);
	private static final long serialVersionUID = 2598426539166789516L;
	private static final String PN_REDIRECT_TARGET = "cq:redirectTarget";
	private static final String RECOMMENDED_PRODUCT_CARD = "recommendedProductCard";
	private static final String REF = "ref";
	private static final String TYPE_DELIMETER = "=";
	private static final String VALUE_DELIMETER = ",";
	private static final String[] IMAGE_TYPES = {".jpeg", ".png", ".jpg"};
	private static final String[] SVG_TYPE = {".svg"};
	private static final String BOOKINGHEADER_PATH = "rcudxp/components/ea/content/booking/bookingheader";
	private static final String INFORMATION_ALERT_PATH = "rcudxp/components/ea/content/cards/infoalertsection";
	private static final String SECTION_TYPE_KEY = "mobapp_sectionType";
	private static final String SECTION_DESCRIPTION = "mobapp_description";
	private static final String SECTION_TYPE_BOOKINGS = "mobapp_booking_header";
	private static final String EXP_IMAGE_PATH_LOCATION = "exp_imagePathExpLoc";
	private static final String EXP_IMAGE_ALT_TEXT = "exp_imgAltText";
	private static final String EXP_BUTTON_TEXT = "exp_buttonText";
	private static final String EXP_PAGE_PATH = "experiencePagePath";
	private static final String AEM_DAM_VIDEO_LINK = "aemDamVideoLink";
	private transient List<String> excludePropertiesList = null;
	private transient List<String> excludeTypesList = null;
	private transient List<String> getProductDetailsPropertiesList = null;
	private transient List<String> referencePropertiesList = null;
	private transient List<String> renamePropertiesList = null;
	boolean tidy = false;
	private long limit = -1L;
	
	private transient SlingHttpServletRequest request;

	@Reference
	private transient DynamicMediaService dynamicMediaService;

	@Reference
	private transient PageJSONServletService pageJsonServletService;

	/**
	 * Update response given request information
	 *
	 * @param slingRequest the request data
	 * @param slingResponse the response data
	 * @throws IOException
	 */
	@Override
	protected void doGet(final SlingHttpServletRequest slingRequest, final SlingHttpServletResponse slingResponse)
			throws IOException {
		try {
			this.request = slingRequest;
			slingResponse.setCharacterEncoding(ENCODING_TYPE_UTF_8);
			slingResponse.setHeader(CONTENT_TYPE, APPLICATION_JSON);
			excludePropertiesList = covertArrayToList(pageJsonServletService.getExcludedProperties());
			referencePropertiesList = covertArrayToList(pageJsonServletService.getReferenceProperties());
			renamePropertiesList = covertArrayToList(pageJsonServletService.getRenameProperties());
			excludeTypesList = covertArrayToList(pageJsonServletService.getExcludedTypes());
			getProductDetailsPropertiesList = covertArrayToList(pageJsonServletService.getProductPageDetailsProperties());
			long counter = 0L;
			limit = pageJsonServletService.getLimit();

			Resource currentResource = slingRequest.getResource();
			tidy = hasSelector(slingRequest, TIDY);



			if (resourceNotExists(currentResource)) {
				throw new ResourceNotFoundException("No data to render.");
			}

			currentResource = getCurrentResource(currentResource);

			if(currentResource != null) {
				JSONNode pageTree = new JSONNode(currentResource.getName(), getFilteredValueMap(currentResource.getValueMap()));
				if(request != null){
					Page currentPage = getCurrentPage(request);
					if(currentPage != null) {
						InheritanceValueMap ivm = new HierarchyNodeInheritanceValueMap(currentPage.getContentResource());
						getInheritedExpProp(ivm,pageTree);
						pageTree.addProperty(CommerceConstants.HIDE_WISH_LIST, getHideWishlist(currentPage));
					}
				}
				collectChild(pageTree, currentResource, slingRequest, null, counter);
				slingResponse.getWriter().write(getJsonString(pageTree));
			}

		} catch (IOException ioException) {
			LOGGER.error("Error in doGet of GetPageJsonServlet: {}", ioException.getMessage());
		} finally {
			slingResponse.getWriter().close();
		}
	}
	Page getCurrentPage(SlingHttpServletRequest request){
		ResourceResolver resourceResolver = request.getResourceResolver();
		Resource currentResource = request.getResource();
		PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
		if (pageManager != null) {
			return pageManager.getContainingPage(currentResource);
		}
		return null;
	}

	private void getInheritedExpProp(InheritanceValueMap ivm, JSONNode pageTree){
		String imagePathExpLoc = Objects.nonNull(ivm.getInherited(CommerceConstants.EXP_IMAGE_PATH,String.class))? ivm.getInherited(CommerceConstants.EXP_IMAGE_PATH,String.class) : StringUtils.EMPTY;
		String imgAltText = Objects.nonNull(ivm.getInherited(CommerceConstants.EXP_IMAGE_ALT_TEXT,String.class)) ? ivm.getInherited(CommerceConstants.EXP_IMAGE_ALT_TEXT,String.class) : StringUtils.EMPTY;
		String buttonText = Objects.nonNull(ivm.getInherited(CommerceConstants.EXP_BUTTON_TEXT,String.class)) ? ivm.getInherited(CommerceConstants.EXP_BUTTON_TEXT,String.class) : StringUtils.EMPTY;
		pageTree.addProperty(EXP_IMAGE_PATH_LOCATION,StringUtils.isNotEmpty(imagePathExpLoc) && StringUtils.isNotEmpty(dynamicMediaService.getDynamicMediaUrl(imagePathExpLoc)) ? dynamicMediaService.getDynamicMediaUrl(imagePathExpLoc) : StringUtils.EMPTY);
		pageTree.addProperty(EXP_IMAGE_ALT_TEXT,imgAltText);
		pageTree.addProperty(EXP_BUTTON_TEXT,buttonText);
	}

	private Resource getCurrentResource(Resource currentResource) {
		Resource jcrResource = currentResource.getChild(JCR_CONTENT);
		if (nonNull(jcrResource) && jcrResource.getValueMap().containsKey(PN_REDIRECT_TARGET)) {
			String newResource = jcrResource.getValueMap().get(PN_REDIRECT_TARGET, String.class);
			if (nonNull(newResource) && !newResource.equals(currentResource.getPath())
					&& !resourceNotExists(currentResource.getResourceResolver().getResource(newResource))) {
				jcrResource = currentResource.getResourceResolver().getResource(newResource);
			}
		}
		return jcrResource;
	}

	/**
	 * Check if the resource provide is a null resource or can't be used
	 *
	 * @param resource the resource to check
	 * @return true if the resource doesn't exist, or if it has no child jcr:content
	 */
	private boolean resourceNotExists(Resource resource) {
		return ResourceUtil.isNonExistingResource(resource)
				|| ResourceUtil.isNonExistingResource(resource.getChild(JCR_CONTENT));
	}

	/**
	 * Recursively search through the resource structure to find child resources and append them to the page tree
	 *
	 * @param pageTree the JSON node being built recursively
	 * @param resource the current resource to build the children of in pageTree
	 * @param request the slingHttpServletRequest
	 * @param prefix adds a prefix to the node name if present
	 * @param counter sense check to stop this servlet from calling itself infinitely
	 */
	private void collectChild(JSONNode pageTree, Resource resource, SlingHttpServletRequest request, String prefix, long counter) {
		counter++;
		if (counter <= limit || limit < 0) {
			Iterator<Resource> children = resource.listChildren();
			while (children.hasNext()) {
				Resource child = children.next();
				String nodeName = (prefix == null) ? child.getName() : (prefix + child.getName());
				nodeReferenceProperties(pageTree, resource, request, counter, child, nodeName);
			}
		}
	}

	private void nodeReferenceProperties(JSONNode pageTree, Resource resource, SlingHttpServletRequest request, long counter, Resource child, String nodeName) {
		if (excludeTypesList.isEmpty() || !isNodeExcluded(child.getValueMap())) {
			JSONNode nodeToAdd = pageTree.addChild(nodeName, getFilteredValueMap(child.getValueMap()));
			if(child.getValueMap().containsKey(SLING_RESOURCE_TYPE_PROPERTY) &&
					getProductDetailsPropertiesList.contains((String) child.getValueMap().get(SLING_RESOURCE_TYPE_PROPERTY)) ){
				addProductDetailPagesToTree(child, nodeToAdd, request.getResourceResolver());
			}
			Iterator<Resource> resourceChildIterator = resource.listChildren();
			if (resourceChildIterator.hasNext()) {
				collectChild(nodeToAdd, child, request, null, counter);
			}
			addNodeReferencePropertiesToTree(request, counter, child, nodeToAdd);
		}
	}

	private void addNodeReferencePropertiesToTree(SlingHttpServletRequest request, long counter, Resource child, JSONNode nodeToAdd) {
		if (referencePropertiesList.isEmpty()) {
			return;
		}
		for (String refProp : referencePropertiesList) {
			Object reference = child.getValueMap().get(refProp);
			if (reference != null) {
				Resource referenceResource = request.getResourceResolver().getResource(reference.toString());
				if (nonNull(referenceResource) && !ResourceUtil.isNonExistingResource(referenceResource)) {
					collectChild(nodeToAdd, referenceResource, request, REF + UNDERSCORE + refProp + UNDERSCORE, counter);
				}
			}
		}
	}


	private void addProductDetailPagesToTree(Resource carouselResource, JSONNode pageTree, ResourceResolver resolver) {
		Resource currentPageResource = resolver.getResource(carouselResource.getPath());
		if (currentPageResource == null) {
			return;
		}
		Optional<Page> getPageFromResourcePath = Optional.ofNullable(resolver.getResource(currentPageResource.getPath().split(FORWARD_SLASH_STRING + JCR_CONTENT)[0]).adaptTo(Page.class));
		if(!getPageFromResourcePath.isPresent()){
			return;
		}
		Page currentPage = getPageFromResourcePath.get();
		ExperienceCardsCarousel cardsCarousel = carouselResource.adaptTo(ExperienceCardsCarousel.class);
		if (cardsCarousel == null || ResourceUtil.isNonExistingResource(carouselResource)) {
			return;
		}
		cardsCarousel.setCurrentPage(currentPage);

		List<HashMap<String, Object>> cardsMapList = cardsCarousel.getExperienceCardDetailsMap();
		for (HashMap<String, Object> map : cardsMapList){
			if(map.get(TITLE) != null) {
				map.put(TYPE, RECOMMENDED_PRODUCT_CARD);
				if(map.containsKey(CommerceConstants.HIDE_WISH_LIST)){
					if(map.get(EXP_PAGE_PATH) != null){
						String expPagePath = map.get(EXP_PAGE_PATH).toString();
						// Adapt the PageManager from the ResourceResolver
						PageManager pageManager = resolver.adaptTo(PageManager.class);
						// Get the Page from the path
						Page expPage = pageManager != null ? pageManager.getPage(expPagePath) : null;
                        if (expPage != null) {
                            map.put(CommerceConstants.HIDE_WISH_LIST,getHideWishlist(expPage));
                        }
                    }

				}
				pageTree.addChild(map.get(TITLE).toString(), getFilteredHashMap(map));
			}
		}
	}

	private boolean getHideWishlist(Page currentPage) {
		Page homepage = currentPage.getAbsoluteParent(CommonConstants.HOME_PAGE_LEVEL);

		if(homepage != null){
			String hideWishlistValue = homepage.getProperties().get(CommerceConstants.HIDE_WISH_LIST, String.class);
			if (CommonConstants.TRUE.equalsIgnoreCase(hideWishlistValue)) {
				return true;
			} else if (currentPage.getProperties().containsKey(CommerceConstants.HIDE_WISH_LIST)) {
				hideWishlistValue = currentPage.getProperties().get(CommerceConstants.HIDE_WISH_LIST, String.class);
				if (CommonConstants.TRUE.equalsIgnoreCase(hideWishlistValue)) {
					return true;
				}
			}
		}
		return false;
	}

	private HashMap<String, Object> getFilteredHashMap(HashMap<String, Object> hashMap) {
		HashMap<String, Object> propMap = new HashMap<String, Object>();
		for (Entry<String, Object> entry : hashMap.entrySet()) {
			filterEntry(propMap, entry);
		}
		return propMap;
	}

	/**
	 * Checks fields of a given resource and accepts them if they are an allowed object type (Calendar, Boolean, Long, Integer, Double, Array)
	 * If not, then adds toString value of the field
	 *
	 * @param valueMap
	 * @return
	 */
	private HashMap<String, Object> getFilteredValueMap(ValueMap valueMap) {
		HashMap<String, Object> propMap = new HashMap<String, Object>();
			for (Entry<String, Object> entry : valueMap.entrySet()) {
				filterEntry(propMap, entry);
			}
		return propMap;
	}

	private void filterEntry(HashMap<String, Object> propMap, Entry<String, Object> entry) {
		String key = entry.getKey();
		if (!excludePropertiesList.contains(key)) {
			Object value = entry.getValue();
			if(isNull(value)){
				return;
			}
			key = getKeyNewName(renamePropertiesList, key);

			if ((value instanceof InputStream)) {
				propMap.put(key, 0);
			} else if ((value instanceof Calendar)) {
				propMap.put(key, format((Calendar) value));
			} else if ((value instanceof Boolean)) {
				propMap.put(key, value);
			} else if ((value instanceof Long)) {
				propMap.put(key, value);
			} else if ((value instanceof Integer)) {
				propMap.put(key, value);
			} else if ((value instanceof Double)) {
				propMap.put(key, value);
			} else if (value.getClass().isArray()) {
				propMap.put(key, value);
			}else if(value instanceof String && ((String) value).contains(INFORMATION_ALERT_PATH)
					&&  Objects.nonNull(propMap.get(CommonConstants.DESCRIPTION))){
				propMap.put(CommonConstants.DESCRIPTION,getMobAppRte((String) propMap.get(CommonConstants.DESCRIPTION)));
			}else if (value instanceof String && Arrays.stream(IMAGE_TYPES).anyMatch((((String) value).toLowerCase())::contains)){
				propMap.put(key, dynamicMediaService.getDynamicMediaUrl((String) value));
			}else if (value instanceof String && ((String) value).contains(BOOKINGHEADER_PATH)
				&& isNull(propMap.get(SECTION_TYPE_KEY))) {
				propMap.put(SECTION_TYPE_KEY, SECTION_TYPE_BOOKINGS);
				propMap.put(key, value.toString());
			}else if (value instanceof String && ((String) value).contains(BOOKINGHEADER_PATH)
				&& isNull(propMap.get(SECTION_DESCRIPTION))) {
                propMap.put(SECTION_DESCRIPTION, propMap.get("subTitle"));
			}else {
				if(Objects.nonNull(propMap.get(SECTION_DESCRIPTION))){
					propMap.put(SECTION_DESCRIPTION,getMobAppRte((String) propMap.get(SECTION_DESCRIPTION)));
				}else if(Objects.nonNull(propMap.get("subTitle"))){
					propMap.put(SECTION_DESCRIPTION, getMobAppRte((String) propMap.get("subTitle")));
				}
				propMap.put(key, value.toString());

			}
			String domain = getDomain(request);
			if(key.equals("imagePath") && Arrays.stream(SVG_TYPE).anyMatch((((String) value).toLowerCase())::contains)) {
				propMap.put("imagePath", domain + value.toString());
			}
			if(key.equals("mediaType") && value.equals("aemHostedVideo")) {
				String videoPath = propMap.get("mediaUrl") != null ? propMap.get("mediaUrl").toString() : StringUtils.EMPTY;
				propMap.put("mediaUrl", domain + videoPath);
			}
			if(key.equals(ASSET_TYPE) && value.equals(VIDEO)) {
				String assetMobilePath = propMap.get(ASSET_PATH) != null ? propMap.get(ASSET_PATH).toString() : StringUtils.EMPTY;
				propMap.put(ASSET_PATH,domain + assetMobilePath);
			}
			if(key.equals(AEM_DAM_VIDEO_LINK) && value instanceof String) {
				String aemDamVideoLink = propMap.get(AEM_DAM_VIDEO_LINK).toString();
				if (StringUtils.isNotEmpty(aemDamVideoLink) && !aemDamVideoLink.startsWith(CommonConstants.HTTP)) {
					propMap.put(AEM_DAM_VIDEO_LINK, domain + aemDamVideoLink);
				}
			}
			if(key.equals(CommerceConstants.HIDE_WISH_LIST) && value instanceof String){
				boolean hideWishListVal = Boolean.parseBoolean(propMap.get(CommerceConstants.HIDE_WISH_LIST).toString());
				propMap.put(CommerceConstants.HIDE_WISH_LIST, hideWishListVal);
			}
			if(key.equals(CommerceConstants.MANUAL_PRODUCT_TAG) && value instanceof String) {
				String manualProductTag = propMap.get(CommerceConstants.MANUAL_PRODUCT_TAG).toString();
				if (manualProductTag.equals(CommonConstants.NONE)) {
					propMap.put(CommerceConstants.MANUAL_PRODUCT_TAG, StringUtils.EMPTY);
				}
			}
			if(key.equals(CommerceConstants.MANUAL_PRODUCT_TAG_COLOR) && value instanceof String) {
				String manualProductTagColor = propMap.get(CommerceConstants.MANUAL_PRODUCT_TAG_COLOR).toString();
				if (manualProductTagColor.equals(CommonConstants.HYPHEN)) {
					propMap.put(CommerceConstants.MANUAL_PRODUCT_TAG_COLOR, StringUtils.EMPTY);
				}
			}
		}
	}
	private String getMobAppRte(String rteDescription){
		if (StringUtils.isNotEmpty(rteDescription)) {

			if (rteDescription.contains("href")) {
				rteDescription = rteDescription.replace(".html", "");
			}
			rteDescription = rteDescription.replaceAll("<span class=\"display-as-h1\">(.*?)</span>", "<h1>$1</h1>");
			rteDescription = rteDescription.replaceAll("<span class=\"rte-strikethrough\">(.*?)</span>", "<s>$1</s>");
			rteDescription = rteDescription.replace("<span class=\"rte-greenfont\">", "<span style=\"color:#30533E;\">");
			return rteDescription;
		}
		return StringUtils.EMPTY;
	}

	private String getKeyNewName(List<String> renamePropertiesList, String key) {
		if (!renamePropertiesList.isEmpty()) {
			for (String prop : renamePropertiesList) {
				String[] renameProp = prop.split(TYPE_DELIMETER);
				if (renameProp.length >= 2 && renameProp[0].equals(key)) {
					key = renameProp[1];
					break;
				}
			}

		}
		return key;
	}

	/**
	 * converts Json node to string for response
	 * @param pageTree
	 * @return
	 */
	private String getJsonString(JSONNode pageTree) {
		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(pageTree).replaceAll(",\"childnodes\":\\[\\]", StringUtils.EMPTY);
		if (tidy) {
			gson = new GsonBuilder().setPrettyPrinting().create();
			JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
			JsonElement jsonElement = gson.fromJson(jsonObject.toString(), JsonElement.class);
			json = gson.toJson(jsonElement);
		}

		return json;
	}

	/**
	 * Remove duplicate values from a list
	 *
	 * @param props the original list possibly containing duplicates
	 * @return the updated list, free of duplicates
	 */
	private List<String> removeDuplicates(List<String> props) {
		List<String> newList = new ArrayList<String>();
		for (String ele : props) {
			if (!newList.contains(ele)) {
				newList.add(ele.trim());
			}
		}
		return newList;
	}

	/**
	 * Given an array of Strings, return a list of strings with any duplicates removed
	 * @param props the String[] to convert
	 * @return the equivalent list of strings
	 */
	private List<String> covertArrayToList(String[] props) {
		List<String> list = Arrays.asList(props);
		list = removeDuplicates(list);
		return list;
	}

	public static String format(Calendar date) {
		Locale DATE_FORMAT_LOCALE = Locale.US;
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", DATE_FORMAT_LOCALE);
		formatter.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
		return formatter.format(date.getTime());
	}

	/**
	 * Check if a given SlingHttpServletRequest has a selector matching the one that we are looking for
	 * @param request the request
	 * @param selectorToCheck the selector whose presence we are checking
	 * @return true if a match is found, false otherwise
	 */
	protected boolean hasSelector(SlingHttpServletRequest request, String selectorToCheck) {
		for (String selector : request.getRequestPathInfo().getSelectors()) {
			if (selectorToCheck.equals(selector)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if node should be excluded based on if its properties match any set by OSGI config
	 * @param properties
	 * @return
	 */
	private boolean isNodeExcluded(ValueMap properties){
		HashMap<String, String[]> excludedPropertiesMap = splitStringToMap(excludeTypesList);

		Iterator<String> itr = excludedPropertiesMap.keySet().iterator();
		while (itr.hasNext()) {
			String property = itr.next();
			if(excludedPropertiesMap.containsKey(property) && properties.get(property) instanceof String &&
					Arrays.asList(excludedPropertiesMap.get(property)).contains(properties.get(property))) {
				return true;
			}
		}
		return false;
	}

	private HashMap<String, String[]> splitStringToMap(List<String> entryList){
		HashMap<String, String[]> entryMap = new HashMap<>();
		for(String entry : entryList){
			String key = entry.contains(TYPE_DELIMETER) ? entry.substring(0, entry.indexOf(TYPE_DELIMETER)) : StringUtils.EMPTY;
			String[] value = entry.contains(TYPE_DELIMETER) ? entry.substring(entry.indexOf(TYPE_DELIMETER)+1).split(VALUE_DELIMETER) : new String[] {StringUtils.EMPTY};
			entryMap.put(key, value);
		}
		return entryMap;
	}

	public String getDomain(SlingHttpServletRequest request) {
	    // Get the full request URL
	    String scheme = request.getScheme(); // http or https
	    String serverName = request.getServerName(); // domain name or IP address
	    //int serverPort = request.getServerPort(); // port number

	    // Construct the domain URL
	    String domain = scheme + "://" + serverName;


	    return domain;
	}
}