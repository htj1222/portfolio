bool MulticastJoinRequest(CTCServerQueueDataPtr& _TCServerQueueDataPtr)
{
	/*
	 struct ip_mreq
	{
			struct in_addr imr_multiaddr;   // IP multicast address of group
			struct in_addr imr_interface;   // local IP address of interface
	};
	 */

	struct ip_mreq* mreq = new struct ip_mreq;
	mreq->imr_multiaddr.s_addr = inet_addr(pTCServerQueueData->m_streamerKey.first.c_str());
	mreq->imr_interface.s_addr = inet_addr(receiver->GetSourceInterfaceIp().c_str());

	// imr_multiaddr는 우리가 참여할 그룹주소를 말한다.
	if( setsockopt(receiver->GetMulticastSocket(), IPPROTO_IP, IP_ADD_MEMBERSHIP, (char *)mreq, sizeof(struct ip_mreq)) < 0 )
	{
		theLogger.error(WHERE(), "join (%s) failed : %d", pTCServerQueueData->m_streamerKey.first.c_str(), errno);
		return false;
	}

	// IP_MULTICAST_IF : 일반적으로 시스템 관리자는 기본 인터페이스를 지정하며, 멀티캐스트 데이터그램은 그쪽으로 보내진다.
	// 이 옵션을 사용하여 지정된 기본 인터페이스를 무시하고 정해진 소켓에서 사용할 인터페이스를 선택할 수 있다.
	if ( setsockopt(receiver->GetMulticastSocket(), IPPROTO_IP, IP_MULTICAST_IF, (char *)mreq, sizeof(struct ip_mreq)) < 0 )
	{
		theLogger.error(WHERE(), "set ip multicast if (%s) failed : %d", pTCServerQueueData->m_streamerKey.first.c_str(), errno);
		return false;
	}

	receiver->PostMessageFromListenforReceiver(_TCServerQueueDataPtr);

	return true;
}

bool MulticastLeaveRequest(CTCServerQueueDataPtr& _TCServerQueueDataPtr)
{
	CMulticastLeaveQueueData* pTCServerQueueData = static_cast<CMulticastLeaveQueueData*>(_TCServerQueueDataPtr.get());

	theLogger.debug(WHERE(), "MulticastLeaveRequest : mip(%s), mport(%d)", pTCServerQueueData->m_streamerKey.first.c_str(), pTCServerQueueData->m_streamerKey.second);
	std::map<CStreamerKey, CMasterReceiverThreadPtr>::iterator find_iter = _requestMap.find(pTCServerQueueData->m_streamerKey);
	if( find_iter != _requestMap.end() )
	{
		struct ip_mreq* mreq = new struct ip_mreq;
		mreq->imr_multiaddr.s_addr = inet_addr(pTCServerQueueData->m_streamerKey.first.c_str());
		mreq->imr_interface.s_addr = inet_addr(find_iter->second->GetSourceInterfaceIp().c_str());

		// multicast drop
		if( setsockopt(find_iter->second->GetMulticastSocket(), IPPROTO_IP, IP_DROP_MEMBERSHIP, (char *)(mreq), sizeof(struct ip_mreq)) < 0 )
		{
			theLogger.error(WHERE(), "leave (%s) failed : %d", pTCServerQueueData->m_streamerKey.first.c_str(), errno);
			return false;
		}
		_requestMap.erase(find_iter);

		find_iter->second->PostMessageFromListenforReceiver(_TCServerQueueDataPtr);
	}
	else
	{
		return false;
	}

	return true;
}
