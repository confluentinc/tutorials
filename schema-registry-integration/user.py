class User(object):
    def __init__(self, user_id, name, email):
        self.user_id = user_id
        self.name = name
        self.email = email

    @staticmethod
    def dict_to_user(obj, ctx):
        if obj is None:
            return None

        return User(user_id=obj['user_id'], name=obj['name'], email=obj['email'])

    @staticmethod
    def user_to_dict(user, ctx):
        return dict(user_id=user.user_id, name=user.name, email=user.email)
