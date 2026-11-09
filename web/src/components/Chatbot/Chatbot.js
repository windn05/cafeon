import React, { useState, useEffect, useRef } from 'react';
import './Chatbot.css';
import { Drawer, FloatButton, List, Input, Spin, Typography, Divider } from 'antd';
import { CommentOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { useChatbot } from '../../context/ChatbotContext';

const { Text } = Typography;
const { Search } = Input;

const faqQuestions = [
    '원가율이 가장 높은 메뉴는 무엇인가요?',
    '수리 중인 자산을 알려줘.',
    '우유를 쓰는 메뉴 목록을 보여줘.',
    '강남역점 직원 목록을 보여줘.',
];

const Chatbot = () => {
    const [drawerVisible, setDrawerVisible] = useState(false);
    const [loading, setLoading] = useState(false);
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef(null);

    const { messages, handleSend: sendToBot } = useChatbot();

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        if (drawerVisible) {
            scrollToBottom();
        }
    }, [messages, loading, drawerVisible]);

    const onSearch = async (value) => {
        if (!value) return;
        setInputValue('');
        setLoading(true);
        await sendToBot(value);
        setLoading(false);
    };

    const handleFaqClick = (question) => {
        onSearch(question);
    };

    return (
        <>
            <FloatButton 
                icon={<CommentOutlined />} 
                tooltip="챗봇에게 물어보기"
                onClick={() => setDrawerVisible(true)}
                style={{ right: 24, bottom: 24 }}
            />
            <Drawer
                title="CafeOn 도우미"
                placement="right"
                onClose={() => setDrawerVisible(false)}
                open={drawerVisible}
                mask={false}
                width={400}
                styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }}
            >
                <div className="chatbot-content">
                    <List
                        dataSource={messages}
                        renderItem={item => (
                            <List.Item className={`chatbot-message-item chatbot-message-${item.sender}`}>
                                <div className="chatbot-bubble">
                                    <p>{item.content}</p>
                                </div>
                            </List.Item>
                        )}
                    />
                    {loading && (
                        <List.Item className="chatbot-message-item chatbot-message-bot">
                            <div className="chatbot-bubble">
                                <Spin size="small" />
                            </div>
                        </List.Item>
                    )}
                    <div ref={messagesEndRef} />
                </div>

                <div className="chatbot-faq">
                    <Divider orientation="left" plain><QuestionCircleOutlined /> 이렇게 물어보세요</Divider>
                    <List
                        dataSource={faqQuestions}
                        renderItem={item => (
                            <List.Item onClick={() => handleFaqClick(item)} className="chatbot-faq-item">
                                <Text type="secondary">{item}</Text>
                            </List.Item>
                        )}
                        size="small"
                    />
                </div>

                <div className="chatbot-footer">
                    <Search
                        placeholder="질문을 입력하세요..."
                        enterButton="전송"
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        onSearch={onSearch}
                        loading={loading}
                    />
                </div>
            </Drawer>
        </>
    );
};

export default Chatbot;
