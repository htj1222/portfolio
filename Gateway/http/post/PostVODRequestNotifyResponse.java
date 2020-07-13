package com.castis.http.post;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.castis.agent.NotificationAgent;
import com.castis.dto.HttpResponseDto;
import com.castis.dto.enumeration.ProductType;
import com.castis.dto.exception.CiRuntimeException;
import com.castis.dto.notification.Notification;
import com.castis.dto.response.VodRequestNotificationResponse;
import com.castis.http.HTTPResponse;
import com.castis.util.OutputStreamToString;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class PostVODRequestNotifyResponse extends HTTPResponse {
	static final Logger Logger = LogManager.getLogger(PostVODRequestNotifyResponse.class);
	String currentRestfulUrl;
	String contentBodyStr;

	public PostVODRequestNotifyResponse(String restfulHttpUrl, String bodyStr) {
		currentRestfulUrl = restfulHttpUrl;
		contentBodyStr = bodyStr;
	}

	@Override
	public HttpResponseDto getHTTPResponse() {

		HttpResponseDto response = new HttpResponseDto(HttpResponseStatus.INTERNAL_SERVER_ERROR, "internal server error");
		Logger.debug("VODRequestNotifyResponse process start! contentBodyStr : {} ", contentBodyStr);

		if (currentRestfulUrl.length() == 0 || currentRestfulUrl.length() == 1) {
			response.setResponseStatus(HttpResponseStatus.BAD_REQUEST);
			response.setResponseString("not valid url:(" + currentRestfulUrl + ")");
		} else {
			InputStream inputStream = new ByteArrayInputStream(contentBodyStr.getBytes());
			VodRequestNotificationResponse notificationResponse = new VodRequestNotificationResponse();
			notificationResponse.setResult(VodRequestNotificationResponse.RC_UNKNOWN, VodRequestNotificationResponse.RM_UNKNOWN);
			boolean isSuccess = false;

			Notification notification = null;
			try {
				// notification parsing
				notification = Notification.unmarshaling(inputStream);
				Logger.info("[requestid={}] notification request parsing success. {}", notification.getVodRequestId(), notification.toString().toLowerCase());

				// null check. if fail throw exception
				nullCheck(notification);

				// put notification information to memory
				isSuccess = NotificationAgent.getInstance().putNotification(notification);

				// set response msg
				notificationResponse.setMessageId(UUID.randomUUID().toString());
				notificationResponse.setMessageIdRef(notification.getMessageId());

				// set result
				if (isSuccess) {
					notificationResponse.setResult(VodRequestNotificationResponse.RC_SUCCESS, VodRequestNotificationResponse.RM_SUCCESS);
				} else {
					notificationResponse.setResult(VodRequestNotificationResponse.RC_SAVE_FAIL, VodRequestNotificationResponse.RM_SAVE_FAIL);
				}
			} catch (CiRuntimeException e) {
				notificationResponse.setResult(e.getResultCode(), e.getMessage());
				Logger.error("notification request parsing fail. ciruntime exception {} {}", e.toString().toLowerCase(), contentBodyStr.toString().toLowerCase());
			} catch (JsonParseException e) {
				notificationResponse.setResult(VodRequestNotificationResponse.RC_PARSE_ERROR, VodRequestNotificationResponse.RM_PARSE_ERROR);
				Logger.error("notification request parsing fail. jsonparse exception {} {}", e.toString().toLowerCase(), contentBodyStr.toString().toLowerCase());
			} catch (JsonMappingException e) {
				notificationResponse.setResult(VodRequestNotificationResponse.RC_PARSE_ERROR, VodRequestNotificationResponse.RM_PARSE_ERROR);
				Logger.error("notification request parsing fail. json mapping exception {} {}", e.toString().toLowerCase(), contentBodyStr.toString().toLowerCase());
			} catch (IOException e) {
				notificationResponse.setResult(VodRequestNotificationResponse.RC_PARSE_ERROR, VodRequestNotificationResponse.RM_PARSE_ERROR);
				Logger.error("notification request parsing fail. io exception {} {}", e.toString().toLowerCase(), contentBodyStr.toString().toLowerCase());
			}

			OutputStream outputStream = new OutputStreamToString().outputStream;

			try {
				notificationResponse.marshaling(outputStream);

				response.setResponseStatus(HttpResponseStatus.OK);
				response.setResponseString(outputStream.toString());
				Logger.info("[requestid={}] notification response success. {}", notification.getVodRequestId(), notificationResponse.toString().toLowerCase());
				outputStream.close();

				Logger.debug("URL:({}) response:({})", currentRestfulUrl, response.getResponseString());
			} catch (JAXBException e) {
				Logger.error("jaxb exception {}", e);
				response.setResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
				response.setResponseString("response string make fail");
			} catch (IOException e) {
				Logger.error("io exception {}", e);
				response.setResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
				response.setResponseString("response string make fail");
			}
		}
		return response;
	}

	public boolean stringIsNullOrEmpty(String checkStr) {
		if (checkStr == null || checkStr.isEmpty())
			return true;
		else
			return false;
	}

	public void nullCheck(Notification notification) {

		if (stringIsNullOrEmpty(notification.getMessageId())) {
			Logger.error("notificaion messageId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getVodRequestId())) {
			Logger.error("notificaion vodRequestId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getVodRequestTime())) {
			Logger.error("notificaion vodRequestTime is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}

		// asset check start -------------------------------------------------
		if (notification.getAsset() == null) {
			Logger.error("notificaion asset info is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getMasterId())) {
			Logger.error("notificaion asset-masterId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getAssetId())) {
			Logger.error("notificaion asset-assetId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getRating())) {
			Logger.error("notificaion asset-rating is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getRunningTime())) {
			Logger.error("notificaion asset-runningTime is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (notification.getAsset().getFileList() == null || notification.getAsset().getFileList().get(0) == null) {
			Logger.error("notificaion asset-fileList is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getFileList().get(0).getResolution())) {
			Logger.error("notificaion asset-fileList-file-resolution is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getProvider())) {
			Logger.error("notificaion asset-provider is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getGenre())) {
			Logger.error("notificaion asset-genre is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getAsset().getContentName())) {
			Logger.error("notificaion asset-contentName is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		// asset check end -------------------------------------------------

		// product check -------------------------------------------------
		if (notification.getProduct() == null) {
			Logger.error("notificaion product is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (ProductType.valueof(notification.getProduct().getProductType()) == ProductType.UNKNOWN) {
			Logger.error("notificaion product-productType is unknown value {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (notification.getProduct().getPayAmount() == null) {
			Logger.error("notificaion product-payAmount is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (notification.getProduct().getPayAmount() < 0) {
			Logger.error("notificaion product-payAmount is minus value {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getProduct().getPermanentLental())) {
			Logger.error("notificaion product-permanentLental is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (notification.getProduct().getPermanentLental().equalsIgnoreCase("true") == false
				&& notification.getProduct().getPermanentLental().equalsIgnoreCase("false") == false) {
			Logger.error("notificaion product-permanentLental is not 'true' or 'false' {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		// product check end -------------------------------------------------

		// user check -------------------------------------------------
		if (notification.getUser() == null) {
			Logger.error("notificaion user is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getUser().getUserDeviceModel())) {
			Logger.error("notificaion user-userDeviceModel is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getUser().getUserId())) {
			Logger.error("notificaion user-userId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		// user check end -------------------------------------------------

		if (notification.getRegion() == null) {
			Logger.error("notificaion regin is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getRegion().getRegionId())) {
			Logger.error("notificaion regin-regionId is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}

		if (notification.getWatchInfo() == null) {
			Logger.error("notificaion watchInfo is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (stringIsNullOrEmpty(notification.getWatchInfo().getResume())) {
			Logger.error("notificaion watchInfo-resume is null {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}
		if (notification.getWatchInfo().getResume().equalsIgnoreCase("Y") == false && notification.getWatchInfo().getResume().equalsIgnoreCase("N") == false) {
			Logger.error("notificaion watchInfo-resume is not 'Y' or 'N' {}", notification);
			throw new CiRuntimeException(VodRequestNotificationResponse.RM_PARSE_ERROR, VodRequestNotificationResponse.RC_PARSE_ERROR);
		}

	}
}
