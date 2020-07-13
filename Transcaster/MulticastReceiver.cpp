std::deque<CTCServerQueueDataPtr> m_DequeForReceiver;

bool Run()
{
	int iReceivedBytes = 0;
	unsigned char* currentRecvPtr = NULL;

	// for http bitrate API
	CalculateBitratePerSecond();

	// Add or Remove session
	ProcessSession();

	// check burst and process when burst flow control
	IsBurstNow();

	// Get only 1 Stream packet
	if( ReceiveN((char **)&currentRecvPtr, RECV_MAX_SIZE, &iReceivedBytes ) == false)
	{
		if(_dest_addresses.size() != 0 && _isEpg == false)
		{
			theLogger.error(WHERE(), nu_get_last_error(), "read fail, multicast ip = %s, Port = %d", _multicast_address.c_str(), _multicast_port);
			_isReadFail = true;
		}
		return CCiThread2::Run();
	}

	if ( iReceivedBytes == 0 )
	{
		castis::msleep(1);
		return CCiThread2::Run();
	}

	//make DOCSIS header
	MakeDOCSISHeader(currentRecvPtr, iReceivedBytes, _multicast_address, _multicast_port, _TranscasterServerConfig->GetDocsisSrcMac(), _dsid);
	// recvPtr move to first position after make DOCSIS header
	currentRecvPtr -= TOTAL_RECV_HEADER_SIZE;

	// Send 1 packet to MulticastSender thread
	SendBufferToSenderThread(currentRecvPtr, iReceivedBytes);
	_sendPacketCount++;

	return CCiThread2::Run();
}

bool PostMessageFromReceiverforStreamer(CTCServerQueueDataPtr& _TCServerQueueDataPtr)
{
	if ((IsThreadDone() == true) || (m_bExit == true))
	{
		theLogger.error(WHERE(), "Failed to PostMessage for MulticastStreamer from Receiver");
		return false;
	}

	boost::mutex::scoped_lock scoped_lock(m_DequeForListenmutex);
	m_DequeForReceiver.push_back(_TCServerQueueDataPtr);

	return true;
}

bool GetNextMessageFromReceiverforStreamer(CTCServerQueueDataPtr& _TCServerQueueDataPtr)
{
	if ((IsThreadDone() == true) || (m_bExit == true))
	{
		theLogger.error(WHERE(), "Failed to GetMessage for MulticastStreamer from Receiver");
		return false;
	}

	if(m_DequeForReceiver.empty() == true)
		return false;

	boost::mutex::scoped_lock scoped_lock(m_DequeForListenmutex);

	//일정 수치 이상 쌓이면 화면이 딜레이되므로 모두 버린다.
	if ( m_DequeForReceiver.size() >= RECEIVER_MAX_QUEUE_SIZE )
	{
		theLogger.error(WHERE(), "MulticastReceiver multicast(%s:%7d) over capacity!! capacity(%d), queue size(%d), all queue drop"
			, _multicast_address.c_str(), _multicast_port, RECEIVER_MAX_QUEUE_SIZE, m_DequeForReceiver.size() );
		m_DequeForReceiver.clear();
	}
	else
	{
		_TCServerQueueDataPtr = *(m_DequeForReceiver.begin());
		m_DequeForReceiver.pop_front();
	}

	return true;
}

void ProcessSession()
{
	std::vector<CTCServerQueueDataPtr> queueMessages;

	if ( m_QueueForListen.empty() == false )
	{
		boost::mutex::scoped_lock scoped_lock(m_QueueForListenmutex);
		queueMessages = m_QueueForListen;
		m_QueueForListen.clear();
	}

	for ( std::vector<CTCServerQueueDataPtr>::iterator itr = queueMessages.begin(); itr != queueMessages.end(); itr++ )
	{
		CTCServerQueueDataPtr pTCServerListenQueueDataPtr = (*itr);
		switch(pTCServerListenQueueDataPtr->m_TCServerMessageType)
		{
		case TC_MESSAGETYPE_ADD_SESSION:
			AddSession(pTCServerListenQueueDataPtr);
			break;

		case TC_MESSAGETYPE_REMOVE_SESSION:
			RemoveSession(pTCServerListenQueueDataPtr);
			break;

		default:
			break;
		}
	}
}

