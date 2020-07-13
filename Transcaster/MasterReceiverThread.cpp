class CRecvBuffer
{
public:
	CRecvBuffer()
		: _bufPtr(NULL)
	{}
	virtual ~CRecvBuffer() {}

public:
	boost::scoped_array<unsigned char> _buf;
	unsigned char* _bufPtr;
};

bool InitInstance()
{
	theLogger.info(WHERE(), "Make MasterReceiverThread buffer pool count(%d)...", _TranscasterServerConfig->GetMasterReceiverBufferPoolCount());
	for( int i = 0 ; i < _TranscasterServerConfig->GetMasterReceiverBufferPoolCount() ; i++ )
	{
		CRecvBuffer* recvBuffer = new CRecvBuffer();
		recvBuffer->_buf.reset(new unsigned char [_bufferSize]);
		recvBuffer->_bufPtr = recvBuffer->_buf.get();

		m_recvBuffers.push_back(recvBuffer);
	}

	theLogger.info(WHERE(),"bindCpuId:(%d)", _bindCpuId);
	if ( _bindCpuId > 0 )
	{
		// 현재 thread에서 특정 cpu core 를 사용하도록 affinity 설정
		cpu_set_t cpuset;
		CPU_ZERO(&cpuset);       //clears the cpuset
		CPU_SET( _bindCpuId , &cpuset); //set CPU @ on cpuset
		int s = pthread_setaffinity_np(m_ciThreadHandle, sizeof(cpuset), &cpuset);
		if( s != 0 )
		{
			theLogger.error(WHERE(),"master receiver bind fail cpuId:(%d)", _bindCpuId);
		}
		else
		{
			theLogger.info(WHERE(),"master receiver bind cpuId:(%d)", _bindCpuId);
		}
	}

	//socket 생성
	if( _socket < 0 )
	{
		if( CreateMulticastSocket() == false )
		{
			theLogger.error(WHERE(), "CreateMulticastSocket Fail. (%s)", _sourceInterfaceIp.c_str());
			return false;
		}
	}

	if( _rawsocket < 0 )
	{
		if( CreateRawSocket() == false )
		{
			theLogger.error(WHERE(), "CreateRawSocket Fail. (%s)", _sourceInterfaceIp.c_str());
			return false;
		}
	}

	return true;
}

bool CreateRawSocket()
{
	theLogger.info(WHERE(), "CreateRawSocket : ifip(%s)", _sourceInterfaceIp.c_str());

	/*
	 SOCKET socket(
        _ln_ int af  //소켓이 사용할 프로토콜 체계
        _ln_ int type //소켓의 타입
        _ln_ int protocol // 프로토콜 체계 중 실제로 사용할 프로토콜
	)
	*/

	//AF_INET    - TCP, UDP로 통신하고 싶을 때 사용 IPv4 소켓
	//AF_PACKET  - protocol을 직접 사용하고 싶을때

	//SOCK_RAW   - ethernet header를 직접만듬
	//SOCK_DGRAM - ethernet header가 만들어져서 들어옴

	//ETH_P_IP   - IPv4 프로토콜만 반환함
	//ETH_P_ALL  - 데이터링크가 받는 모든 프로토콜을 반환함

	// eth 헤더는 그대로 사용하고, ip, udp 헤더를 확인, 수정하여 사용할 것이기에 아래와 같이 socket 설정한다.
	_rawsocket = socket(AF_PACKET, SOCK_DGRAM, htons(ETH_P_IP));

	if ( _rawsocket < 0 )
		return false;

	// create a interface request structure
	struct ifreq ifr;
	memset(&ifr, 0, sizeof(ifr));

	// set the interface name
	char nic_name[256];
	nu_get_nic_from_ip(nic_name, _sourceInterfaceIp.c_str());
	strncpy(ifr.ifr_name, nic_name, IFNAMSIZ);

	ioctl(_rawsocket, SIOCGIFINDEX, &ifr);
	if ( setsockopt(_rawsocket, SOL_SOCKET, SO_BINDTODEVICE, (void *)&ifr, sizeof(ifr)) < 0)
		theLogger.error(WHERE(), "MasterReceiver(%s) raw socket bind to device error : %d", _sourceInterfaceIp.c_str(), errno);

	struct sockaddr_ll Socket_Addr;
	Socket_Addr.sll_family = AF_PACKET;
	Socket_Addr.sll_protocol = htons(ETH_P_IP);
	Socket_Addr.sll_ifindex = ifr.ifr_ifindex;
	Socket_Addr.sll_pkttype = PACKET_MULTICAST;
	bind(_rawsocket, (struct sockaddr *)&Socket_Addr, sizeof(Socket_Addr));
	nu_set_revbuf(_rawsocket, _TranscasterServerConfig->GetRecvBufferSize());

	struct packet_mreq mreq;
	mreq.mr_ifindex = ifr.ifr_ifindex;
	mreq.mr_type = PACKET_MR_ALLMULTI;
	mreq.mr_alen = 0;
	if ( setsockopt(_rawsocket, SOL_PACKET, PACKET_ADD_MEMBERSHIP, &mreq, sizeof(struct packet_mreq)) < 0 )
		return false;

	int len;

	struct timeval timeout;
	int timeoutSec = _TranscasterServerConfig->GetMulticastReadTimeoutInMilliSec() / 1000;
	int timeoutMilliSec = _TranscasterServerConfig->GetMulticastReadTimeoutInMilliSec() % 1000;

	timeout.tv_sec = timeoutSec;
	timeout.tv_usec = timeoutMilliSec;

	len = sizeof(timeout);
	if ( setsockopt(_rawsocket, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout, len) == -1 )
	{
		theLogger.error(WHERE(), "SetTimeout Failed (%d)", _TranscasterServerConfig->GetMulticastReadTimeoutInMilliSec());
		return false;
	}
	return true;
}

bool CreateMulticastSocket()
{
	theLogger.info(WHERE(), "CreateMulticastSocket : ifip(%s)", _sourceInterfaceIp.c_str());

	_socket = socket(AF_INET, SOCK_DGRAM, 0);
	if ( _socket < 0 )
		return false;

	// create a interface request structure
	struct ifreq ifr;
	memset(&ifr, 0, sizeof(ifr));

	// set the interface name
	char nic_name[256];
	nu_get_nic_from_ip(nic_name, _sourceInterfaceIp.c_str());
	strncpy(ifr.ifr_name, nic_name, IFNAMSIZ);

	ioctl(_socket, SIOCGIFINDEX, &ifr);
	if ( setsockopt(_socket, SOL_SOCKET, SO_BINDTODEVICE, (void *)&ifr, sizeof(ifr)) < 0)
		theLogger.error(WHERE(), "MasterReceiver(%s) socket bind to device error : %d", _sourceInterfaceIp.c_str(), errno);

	return true;
}


int ReceiveData()
{
	int iReceivedBytes = 0;
	if( _rawsocket > 0 )
	{
		CRecvBuffer* recvBuffer = *m_bufferIter;
		m_bufferIter++;
		if( m_bufferIter == m_recvBuffers.end() )
			m_bufferIter = m_recvBuffers.begin();

		// recvPtr은 현재까지 읽은 위치 + 24는 Docis Header를 위한 공간
		unsigned char* currentRecvPtr = recvBuffer->_buf.get() + TOTAL_RECV_HEADER_SIZE;
		iReceivedBytes = recv(_rawsocket, currentRecvPtr, RECV_MAX_SIZE, 0);

		if ( iReceivedBytes > 0 )
		{
			if ( iReceivedBytes > _recvCheckSize )
			{
				theLogger.error(WHERE(), "recv size is over (%d) current recv size : %d", _recvCheckSize , iReceivedBytes);
				return iReceivedBytes;
			}

			// ip 헤더 분석
			struct iphdr* ip_header = (struct iphdr*)currentRecvPtr;

			//받은것이 udp 일 경우에만 처리
			if ( ip_header->protocol == iptype_udp )
			{
				//udp 헤더 분석
				struct udphdr* udp_header = (struct udphdr*)(currentRecvPtr + IP_HEADER_SIZE);
				int dataLength = ntohs(ip_header->tot_len);

				//ip header daddr : multicast ip , udp header dest : destination port
				CReceiveKey key(ip_header->daddr, udp_header->dest);
				std::map<CReceiveKey, CMReqInfo>::iterator exist_iter = m_mreqMap.find(key);

				if( exist_iter != m_mreqMap.end() )
				{
					// check thread running
					if ( isActived() == true )
					{
						CTCServerQueueDataPtr pTCQueueData(new CMulticastDataQueueData(TC_MESSAGETYPE_MULTICAST_DATA_SEND));
						CMulticastDataQueueData* pMulticastDataQueueData = static_cast<CMulticastDataQueueData*>(pTCQueueData.get());

						pMulticastDataQueueData->m_bufferPtr = currentRecvPtr;
						pMulticastDataQueueData->m_size = dataLength;

						//muticast ip port 를 처리하는 receiver thread에 pinter를 전달
						if ( exist_iter->second._receiverPtr->PostMessageFromReceiverforStreamer(pTCQueueData) == false )
						{
							theLogger.warning(WHERE(), "MulticastReceiverThread is done. remove from receiver list. ip(%08X), port(%04X)", key.first, key.second);
						}
					}
					_sendSuccessBytes += iReceivedBytes;
				}
			}

			_receviedBytes += iReceivedBytes;

			recvBuffer->_bufPtr = currentRecvPtr + iReceivedBytes + 4;
		}
	}

	return iReceivedBytes;
}
